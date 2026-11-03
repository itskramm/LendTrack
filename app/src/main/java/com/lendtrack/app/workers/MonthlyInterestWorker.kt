package com.lendtrack.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.LoanDao
import com.lendtrack.app.core.database.dao.MonthlyInterestChargeDao
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.MonthlyInterestChargeEntity
import com.lendtrack.app.core.database.entities.SyncOperationEntity
import com.lendtrack.app.core.sync.SyncTrigger
import com.lendtrack.app.domain.model.LoanStatus
import com.lendtrack.app.domain.model.SyncOperationType
import com.lendtrack.app.domain.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

private const val TAG = "MonthlyInterestWorker"

/**
 * MonthlyInterestWorker — Applies recurring monthly interest charges to active loans.
 *
 * Runs daily at 12:01 AM. On the 1st of each month, it:
 * 1. Gets all active loans with monthlyInterestAmount > 0
 * 2. Checks if charge already exists for the current month
 * 3. Creates a new MonthlyInterestChargeEntity if needed
 * 4. Queues sync operation to Firebase
 *
 * All operations are atomic using database transactions.
 */
@HiltWorker
class MonthlyInterestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: LendTrackDatabase,
    private val loanDao: LoanDao,
    private val monthlyChargeDao: MonthlyInterestChargeDao,
    private val syncDao: SyncDao,
    private val syncTrigger: SyncTrigger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now()
            
            // Only run on the 1st of the month
            if (today.dayOfMonth != 1) {
                Log.d(TAG, "Not the 1st of the month, skipping monthly interest charges")
                return Result.success()
            }

            val yearMonth = YearMonth.from(today).toString() // e.g., "2024-01"
            Log.i(TAG, "Processing monthly interest charges for $yearMonth")

            // Get all active loans with monthly interest enabled
            val activeLoans = loanDao.getActive().filter { loan ->
                loan.status == LoanStatus.ACTIVE && loan.monthlyInterestAmount > 0.0
            }

            if (activeLoans.isEmpty()) {
                Log.d(TAG, "No active loans with monthly interest charges")
                return Result.success()
            }

            var chargesCreated = 0
            val now = LocalDateTime.now()

            for (loan in activeLoans) {
                // Check if charge already exists for this month
                val existingCharge = monthlyChargeDao.getChargeForMonth(loan.id, yearMonth)
                if (existingCharge != null) {
                    Log.d(TAG, "Charge already exists for loan ${loan.id} in $yearMonth")
                    continue
                }

                // Create new monthly charge
                val chargeId = UUID.randomUUID().toString()
                val charge = MonthlyInterestChargeEntity(
                    id = chargeId,
                    loanId = loan.id,
                    borrowerId = loan.borrowerId,
                    amount = loan.monthlyInterestAmount,
                    chargeDate = today,
                    isPaid = false,
                    createdAt = now,
                    syncStatus = SyncStatus.PENDING,
                )

                val syncOp = SyncOperationEntity(
                    id = UUID.randomUUID().toString(),
                    operationType = SyncOperationType.CREATE_MONTHLY_CHARGE,
                    entityId = chargeId,
                    payload = """{"id":"$chargeId","loanId":"${loan.id}","borrowerId":"${loan.borrowerId}","amount":${loan.monthlyInterestAmount},"chargeDate":"$today"}""",
                    idempotencyKey = UUID.randomUUID().toString(),
                    createdAt = now,
                )

                // Insert in transaction
                db.withTransaction {
                    monthlyChargeDao.insert(charge)
                    syncDao.insertOperation(syncOp)
                }

                chargesCreated++
                Log.i(TAG, "Created monthly charge for loan ${loan.id}: ₱${"%.2f".format(loan.monthlyInterestAmount)}")
            }

            if (chargesCreated > 0) {
                syncTrigger.onLocalWrite()
                Log.i(TAG, "Created $chargesCreated monthly interest charges for $yearMonth")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process monthly interest charges: ${e.message}", e)
            Result.retry()
        }
    }
}
