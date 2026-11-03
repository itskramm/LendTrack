package com.lendtrack.app.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lendtrack.app.MainActivity
import com.lendtrack.app.core.database.dao.BorrowerDao
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.core.database.dao.LoanDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Daily WorkManager job — checks Room for installments due today/tomorrow
 * and any overdue installments, then posts Android notifications.
 *
 * Runs at 8 AM local time, scheduled by [DueDateNotificationScheduler].
 * Silently skips if POST_NOTIFICATIONS permission is not granted (Android 13+).
 */
@HiltWorker
class DueDateNotificationWorker @AssistedInject constructor(
    @Assisted context:          Context,
    @Assisted workerParams:     WorkerParameters,
    private val installmentDao: InstallmentDao,
    private val loanDao:        LoanDao,
    private val borrowerDao:    BorrowerDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Permission check — Android 13+
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val today    = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val dueSoon = installmentDao.getDueSoon(today.toString(), tomorrow.toString())
        val overdue = installmentDao.getOverdueSuspend(today.toString())

        if (dueSoon.isEmpty() && overdue.isEmpty()) return Result.success()

        // Build lookup maps
        val loans      = loanDao.observeAll().first().associateBy { it.id }
        val borrowers  = borrowerDao.observeAll().first().associateBy { it.id }

        fun borrowerName(loanId: String): String {
            val loan = loans[loanId] ?: return "Unknown"
            return borrowers[loan.borrowerId]?.name ?: "Unknown"
        }

        val notifManager = NotificationManagerCompat.from(applicationContext)

        val tapIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // ── Due today / tomorrow ───────────────────────────────────────────
        dueSoon.forEach { inst ->
            if (inst.amountPaid >= inst.amount) return@forEach
            val name      = borrowerName(inst.loanId)
            val remaining = inst.amount - inst.amountPaid
            val dueLabel  = if (inst.dueDate.toString() == today.toString()) "TODAY" else "tomorrow"
            val notifId   = NotificationIds.INDIVIDUAL_BASE + (name.hashCode() and 0xFFFF)

            notifManager.notify(
                notifId,
                NotificationCompat.Builder(applicationContext, NotificationChannels.DUE_DATES)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("💸 Payment due $dueLabel — $name")
                    .setContentText("₱${"%.2f".format(remaining)} is due $dueLabel")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("₱${"%.2f".format(remaining)} installment for $name is due $dueLabel. Tap to open LendTrack."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(tapIntent)
                    .setAutoCancel(true)
                    .build()
            )
        }

        // ── Overdue — grouped by borrower ─────────────────────────────────
        overdue
            .filter { it.amountPaid < it.amount }
            .groupBy { borrowerName(it.loanId) }
            .forEach { (name, insts) ->
                val totalOverdue = insts.sumOf { it.amount - it.amountPaid }
                val count        = insts.size
                val notifId      = NotificationIds.INDIVIDUAL_BASE + (name.hashCode() and 0xFFFF) + 10_000

                notifManager.notify(
                    notifId,
                    NotificationCompat.Builder(applicationContext, NotificationChannels.DUE_DATES)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("⚠ Overdue — $name")
                        .setContentText("₱${"%.2f".format(totalOverdue)} across $count installment${if (count != 1) "s" else ""}")
                        .setStyle(NotificationCompat.BigTextStyle()
                            .bigText("$name has $count overdue installment${if (count != 1) "s" else ""} totalling ₱${"%.2f".format(totalOverdue)}. Tap to open LendTrack."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(tapIntent)
                        .setAutoCancel(true)
                        .build()
                )
            }

        return Result.success()
    }
}
