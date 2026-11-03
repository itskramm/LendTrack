package com.lendtrack.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.core.database.dao.LatePenaltyDao
import com.lendtrack.app.core.database.dao.LoanDao
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.LatePenaltyEntity
import com.lendtrack.app.core.database.entities.SyncOperationEntity
import com.lendtrack.app.core.sync.SyncTrigger
import com.lendtrack.app.domain.model.LoanStatus
import com.lendtrack.app.domain.model.SyncOperationType
import com.lendtrack.app.domain.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val TAG = "LatePenaltyWorker"

/**
 * LatePenaltyWorker — Applies late penalties to overdue installments.
 *
 * Runs daily at 8:00 AM. For each overdue installment:
 * 1. Gets the loan's penalty settings (amount and grace days)
 * 2. Calculates days overdue
 * 3. Checks if grace period exceeded
 * 4. Checks if penalty already applied
 * 5. Creates LatePenaltyEntity if conditions met
 * 6. Queues sync operation to Firebase
 *
 * All operations are atomic using database transactions.
 */
@HiltWorker
class LatePenaltyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: LendTrackDatabase,
    private val loanDao: LoanDao,
    private val installmentDao: InstallmentDao,
    private val penaltyDao: LatePenaltyDao,
    private val syncDao: SyncDao,
    private val syncTrigger: SyncTrigger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now()
            Log.i(TAG, "Processing late penalties for $today")

            // Get all overdue installments (due date < today and not fully paid)
            val overdueInstallments = installmentDao.getOverdueSuspend(today.toString())

            if (overdueInstallments.isEmpty()) {
                Log.d(TAG, "No overdue installments found")
                return Result.success()
            }

            Log.d(TAG, "Found ${overdueInstallments.size} overdue installments")
            var penaltiesApplied = 0
            val now = LocalDateTime.now()

            for (installment in overdueInstallments) {
                // Get loan to check penalty settings
                val loan = loanDao.getById(installment.loanId)
                if (loan == null) {
                    Log.w(TAG, "Loan ${installment.loanId} not found for installment ${installment.id}")
                    continue
                }

                // Skip if loan doesn't have late penalties enabled
                if (loan.status != LoanStatus.ACTIVE || loan.latePenaltyAmount <= 0.0) {
                    continue
                }

                // Check if penalty already applied for this installment
                val existingPenalty = penaltyDao.getPenaltyForInstallment(installment.id)
                if (existingPenalty != null) {
                    Log.d(TAG, "Penalty already exists for installment ${installment.id}")
                    continue
                }

                // Calculate days overdue
                val daysOverdue = ChronoUnit.DAYS.between(installment.dueDate, today).toInt()
                
                // Check if grace period exceeded
                if (daysOverdue <= loan.latePenaltyGraceDays) {
                    Log.d(TAG, "Installment ${installment.id} within grace period ($daysOverdue days, grace: ${loan.latePenaltyGraceDays})")
                    continue
                }

                // Apply penalty
                val penaltyId = UUID.randomUUID().toString()
                val penalty = LatePenaltyEntity(
                    id = penaltyId,
                    loanId = loan.id,
                    borrowerId = loan.borrowerId,
                    installmentId = installment.id,
                    amount = loan.latePenaltyAmount,
                    penaltyDate = today,
                    reason = "Overdue payment - $daysOverdue days late",
                    isPaid = false,
                    createdAt = now,
                    syncStatus = SyncStatus.PENDING,
                )

                val syncOp = SyncOperationEntity(
                    id = UUID.randomUUID().toString(),
                    operationType = SyncOperationType.CREATE_LATE_PENALTY,
                    entityId = penaltyId,
                    payload = """{"id":"$penaltyId","loanId":"${loan.id}","borrowerId":"${loan.borrowerId}","installmentId":"${installment.id}","amount":${loan.latePenaltyAmount},"penaltyDate":"$today","reason":"Overdue payment - $daysOverdue days late"}""",
                    idempotencyKey = UUID.randomUUID().toString(),
                    createdAt = now,
                )

                // Insert in transaction
                db.withTransaction {
                    penaltyDao.insert(penalty)
                    syncDao.insertOperation(syncOp)
                }

                penaltiesApplied++
                Log.i(TAG, "Applied penalty to installment ${installment.id}: ₱${"%.2f".format(loan.latePenaltyAmount)} ($daysOverdue days overdue)")
            }

            if (penaltiesApplied > 0) {
                syncTrigger.onLocalWrite()
                Log.i(TAG, "Applied $penaltiesApplied late penalties")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process late penalties: ${e.message}", e)
            Result.retry()
        }
    }
}
