package com.lendtrack.app.core.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DueDateScheduler"

/**
 * Schedules the daily due-date notification check via WorkManager.
 *
 * Fires every day at 8:00 AM local time. Uses a flex window of 30 minutes
 * so WorkManager can batch it with other work.
 *
 * Safe to call repeatedly — WorkManager de-duplicates by unique name with
 * [ExistingPeriodicWorkPolicy.KEEP].
 */
@Singleton
class DueDateNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WORK_NAME = "lendtrack_due_date_check"
        private const val HOUR_OF_DAY = 8   // 8:00 AM
    }

    fun scheduleDailyCheck() {
        try {
            val initialDelay = calculateInitialDelayMillis()

            val request = PeriodicWorkRequestBuilder<DueDateNotificationWorker>(
                repeatInterval         = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval       = 30,
                flexTimeIntervalUnit   = TimeUnit.MINUTES,
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)   // run even on low battery — financial alerts are important
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager schedule error: ${e.message}", e)
        }
    }

    /** Calculate milliseconds until the next 8:00 AM */
    private fun calculateInitialDelayMillis(): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If 8 AM today has already passed, schedule for 8 AM tomorrow
        if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
