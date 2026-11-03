package com.lendtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lendtrack.app.core.notification.DueDateNotificationScheduler
import com.lendtrack.app.core.notification.NotificationChannels
import com.lendtrack.app.core.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "LendTrackApp"

@HiltAndroidApp
class LendTrackApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory:          HiltWorkerFactory
    @Inject lateinit var syncManager:            FirebaseSyncManager
    @Inject lateinit var notificationScheduler:  DueDateNotificationScheduler
    @Inject lateinit var database:               com.lendtrack.app.core.database.LendTrackDatabase
    @Inject lateinit var dataStore:              androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        try {
            syncManager.start()
        } catch (e: Exception) {
            Log.e(TAG, "SyncManager start error: ${e.message}", e)
        }

        try {
            notificationScheduler.scheduleDailyCheck()
        } catch (e: Exception) {
            Log.e(TAG, "NotificationScheduler schedule error: ${e.message}", e)
        }

        scheduleMonthlyInterestWorker()
        scheduleLatePenaltyWorker()

        seedDatabaseIfNeeded()
    }

    /**
     * Schedule MonthlyInterestWorker to run daily at 12:01 AM.
     * It will check if today is the 1st of the month and apply charges if needed.
     */
    private fun scheduleMonthlyInterestWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<com.lendtrack.app.workers.MonthlyInterestWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "monthly_interest_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
            Log.i(TAG, "MonthlyInterestWorker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule MonthlyInterestWorker: ${e.message}", e)
        }
    }

    /**
     * Schedule LatePenaltyWorker to run daily at 8:00 AM.
     * It will check for overdue installments and apply penalties based on loan settings.
     */
    private fun scheduleLatePenaltyWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<com.lendtrack.app.workers.LatePenaltyWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "late_penalty_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
            Log.i(TAG, "LatePenaltyWorker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule LatePenaltyWorker: ${e.message}", e)
        }
    }

    /**
     * Set database_seeded flag to true without populating dummy data.
     */
    private fun seedDatabaseIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val seededKey = booleanPreferencesKey("database_seeded")
                dataStore.edit { prefs ->
                    prefs[seededKey] = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Seed database error: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)

                // Due-date alerts channel
                val dueChannel = NotificationChannel(
                    NotificationChannels.DUE_DATES,
                    "Payment Due Dates",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts for upcoming and overdue loan installments"
                    enableVibration(true)
                }
                manager?.createNotificationChannel(dueChannel)
            } catch (e: Exception) {
                Log.e(TAG, "Notification channel error: ${e.message}", e)
            }
        }
    }
}
