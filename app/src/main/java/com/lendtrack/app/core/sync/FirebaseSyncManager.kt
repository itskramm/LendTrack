package com.lendtrack.app.core.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lendtrack.app.core.database.dao.BorrowerDao
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.core.database.dao.LatePenaltyDao
import com.lendtrack.app.core.database.dao.LoanDao
import com.lendtrack.app.core.database.dao.MonthlyInterestChargeDao
import com.lendtrack.app.core.database.dao.PaymentDao
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.*
import com.lendtrack.app.domain.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG  = "FirebaseSyncManager"
private const val ROOT = "lendtrack"

/**
 * FirebaseSyncManager — direct Android ↔ Firebase RTDB sync.
 *
 * Architecture:
 *   Room (local SQLite) ←→ FirebaseSyncManager ←→ Firebase RTDB
 *
 * How it works:
 * 1. Every write goes to Room first (offline-safe).
 * 2. The write is also queued in [SyncDao] as a PENDING outbox entry.
 * 3. [FirebaseSyncManager] observes the pending count via a Flow.
 * 4. When connectivity is available AND a user is signed in, it drains
 *    the outbox by writing each pending entity directly to RTDB.
 * 5. On success, the entity's [SyncStatus] is updated to SYNCED.
 * 6. On failure, the entry stays PENDING and will be retried on the
 *    next connectivity event.
 *
 * Firebase RTDB offline persistence:
 * Firebase's own offline persistence (.setPersistenceEnabled(true)) is
 * also enabled, so Firebase itself will queue writes when offline and
 * deliver them when connectivity returns — giving us a second safety net.
 *
 * Conflict policy:
 * The device is the single tablet (single writer). The RTDB write always
 * reflects the latest Room state. Last-write-wins is acceptable for this
 * use case.
 *
 * Data path in RTDB:
 *   /lendtrack/{ownerUid}/borrowers/{id}
 *   /lendtrack/{ownerUid}/loans/{id}
 *   /lendtrack/{ownerUid}/installments/{loanId}/{id}
 *   /lendtrack/{ownerUid}/payments/{id}
 */
@Singleton
class FirebaseSyncManager @Inject constructor(
    private val borrowerDao:    BorrowerDao,
    private val loanDao:        LoanDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao:     PaymentDao,
    private val syncDao:        SyncDao,
    private val monthlyInterestChargeDao: MonthlyInterestChargeDao,
    private val latePenaltyDao: LatePenaltyDao,
    private val networkMonitor: NetworkMonitor,
    private val prefs:          SyncPreferences,
) {
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val db: FirebaseDatabase? by lazy {
        try {
            FirebaseDatabase.getInstance("https://lendtrack-239a1-default-rtdb.firebaseio.com")
        } catch (e: Exception) {
            try {
                FirebaseDatabase.getInstance()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to get FirebaseDatabase instance: ${e2.message}")
                null
            }
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FirebaseAuth instance: ${e.message}")
            null
        }
    }

    /** Call once from Application.onCreate() */
    fun start() {
        val firebaseDb = db
        if (firebaseDb != null) {
            try {
                firebaseDb.setPersistenceEnabled(true)
            } catch (e: Exception) {
                Log.w(TAG, "Persistence config: ${e.message}")
            }

            val uid = ownerUid()
            if (uid != null) {
                try {
                    firebaseDb.getReference("$ROOT/$uid").keepSynced(true)
                } catch (e: Exception) {
                    Log.w(TAG, "keepSynced config: ${e.message}")
                }
            }
        }

        // Observe the NetworkMonitor — sync whenever we come online
        networkMonitor.isOnline
            .onEach { online ->
                if (online && isAuthenticated()) {
                    Log.d(TAG, "Online — draining sync outbox.")
                    drainOutbox()
                }
            }
            .launchIn(scope)
    }

    /**
     * Called by repositories immediately after every local write.
     * Attempts an instant push if online; otherwise the outbox drains
     * on the next connectivity event.
     */
    fun onLocalWrite() {
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "onLocalWrite: Device is offline, sync queued")
            return
        }

        if (!isAuthenticated()) {
            Log.e(TAG, "onLocalWrite: User not authenticated, cannot sync")
            return
        }

        scope.launch { drainOutbox() }
    }

    fun triggerSync() {
        scope.launch { drainOutbox() }
    }

    /**
     * Test Firebase connection and authentication.
     * Returns a status message describing the result.
     */
    suspend fun testFirebaseConnection(): String {
        return try {
            val firebaseDb = db ?: return "Error: Firebase database instance is null"
            val uid = ownerUid() ?: return "Error: User not authenticated"

            val testRef = firebaseDb.getReference("$ROOT/$uid/test")
            val testData = mapOf("timestamp" to System.currentTimeMillis())

            Log.d(TAG, "testFirebaseConnection: Writing test data to $ROOT/$uid/test")
            testRef.setValue(testData).await()

            Log.d(TAG, "testFirebaseConnection: Reading back test data")
            val snapshot = testRef.get().await()
            if (snapshot.exists()) {
                Log.d(TAG, "testFirebaseConnection: Test data verified, removing")
                testRef.removeValue().await()
                "Success: Firebase connection working"
            } else {
                "Error: Could not read back test data"
            }
        } catch (e: Exception) {
            Log.e(TAG, "testFirebaseConnection: Failed - ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Process all PENDING sync operations in FIFO order.
     * Each operation reads the current Room entity and pushes it to RTDB.
     */
    private suspend fun drainOutbox() {
        if (!isAuthenticated()) {
            Log.e(TAG, "drainOutbox: User not authenticated, skipping sync")
            return
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "drainOutbox: Device offline, skipping sync")
            return
        }

        val pending = syncDao.getRetryableOperations()
        if (pending.isEmpty()) {
            Log.d(TAG, "drainOutbox: No pending operations")
            return
        }

        Log.d(TAG, "Draining ${pending.size} pending operation(s).")
        var synced = 0

        for (op in pending) {
            val success = processSyncOperation(op)
            if (success) synced++
        }

        if (synced > 0) {
            prefs.setLastSyncAt(LocalDateTime.now().toString())
            syncDao.deleteSynced()
        }
        Log.d(TAG, "Drained $synced/${pending.size} operations.")
    }

    private suspend fun processSyncOperation(op: SyncOperationEntity): Boolean {
        return try {
            val success = syncOperation(op)

            if (success) {
                syncDao.markAttempt(
                    id     = op.id,
                    status = SyncStatus.SYNCED,
                    now    = LocalDateTime.now().toString(),
                )
            } else {
                val newAttemptCount = op.attemptCount + 1
                val newStatus = if (newAttemptCount >= 5) {
                    SyncStatus.REQUIRES_ATTENTION
                } else {
                    SyncStatus.FAILED
                }

                syncDao.markAttempt(
                    id     = op.id,
                    status = newStatus,
                    now    = LocalDateTime.now().toString(),
                    error  = "Sync failed after $newAttemptCount attempts",
                )
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "processSyncOperation: Exception for op ${op.id}: ${e.message}", e)
            val newAttemptCount = op.attemptCount + 1
            val newStatus = if (newAttemptCount >= 5) {
                SyncStatus.REQUIRES_ATTENTION
            } else {
                SyncStatus.FAILED
            }

            syncDao.markAttempt(
                id     = op.id,
                status = newStatus,
                now    = LocalDateTime.now().toString(),
                error  = e.message,
            )
            false
        }
    }

    private suspend fun syncOperation(op: SyncOperationEntity): Boolean {
        return try {
            when (op.operationType) {
                com.lendtrack.app.domain.model.SyncOperationType.CREATE_BORROWER,
                com.lendtrack.app.domain.model.SyncOperationType.UPDATE_BORROWER,
                com.lendtrack.app.domain.model.SyncOperationType.ARCHIVE_BORROWER -> pushBorrower(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.DELETE_BORROWER -> deleteBorrowerFromFirebase(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.CREATE_LOAN,
                com.lendtrack.app.domain.model.SyncOperationType.UPDATE_LOAN,
                com.lendtrack.app.domain.model.SyncOperationType.CANCEL_LOAN     -> pushLoan(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.DELETE_LOAN -> deleteLoanFromFirebase(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.RECORD_PAYMENT,
                com.lendtrack.app.domain.model.SyncOperationType.REVERSE_PAYMENT -> pushPayment(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.DELETE_PAYMENT -> deletePaymentFromFirebase(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.CREATE_MONTHLY_CHARGE -> pushMonthlyCharge(op.entityId)

                com.lendtrack.app.domain.model.SyncOperationType.CREATE_LATE_PENALTY -> pushLatePenalty(op.entityId)

                else -> {
                    Log.w(TAG, "Unknown sync operation type: ${op.operationType}")
                    true  // Mark as done to remove from queue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for op ${op.id}: ${e.message}", e)
            false
        }
    }

    // ── Push individual entities to RTDB ──────────────────────────────────────

    private suspend fun pushBorrower(id: String): Boolean {
        Log.d(TAG, "pushBorrower: Entry point for borrower ID=$id")
        
        val firebaseDb = db
        if (firebaseDb == null) {
            Log.e(TAG, "pushBorrower: Firebase database instance is null")
            return false
        }
        Log.d(TAG, "pushBorrower: Firebase database instance OK")
        
        val entity = borrowerDao.getById(id)
        if (entity == null) {
            Log.i(TAG, "pushBorrower: Entity ID=$id not found in local database (already deleted). Treating as done.")
            return true
        }
        Log.d(TAG, "pushBorrower: Entity retrieved from local DB: name=${entity.name}")
        
        val uid = ownerUid()
        if (uid == null) {
            Log.e(TAG, "pushBorrower: User authentication UID is null (not authenticated)")
            return false
        }
        Log.d(TAG, "pushBorrower: User UID=$uid")

        val path = "$ROOT/$uid/borrowers/${entity.id}"
        Log.d(TAG, "pushBorrower: Writing to Firebase path: $path")
        
        val data = mapOf(
            "id"                           to entity.id,
            "ownerUid"                     to uid,
            "name"                         to entity.name,
            "contactNumber"                to entity.contactNumber,
            "preferredPaymentChannel"      to entity.preferredPaymentChannel.name,
            "secondaryContactName"         to entity.secondaryContactName,
            "secondaryContactNumber"       to entity.secondaryContactNumber,
            "secondaryContactRelationship" to entity.secondaryContactRelationship,
            "governmentIdType"             to entity.governmentIdType,
            "governmentIdNumber"           to entity.governmentIdNumber,
            "address"                      to entity.address,
            "barangay"                     to entity.barangay,
            "occupation"                   to entity.occupation,
            "notes"                        to entity.notes,
            "isArchived"                   to entity.isArchived,
            "createdAt"                    to entity.createdAt.toString(),
            "updatedAt"                    to entity.updatedAt.toString(),
        )
        
        try {
            firebaseDb.getReference(path).setValue(data).await()
            Log.i(TAG, "pushBorrower: Successfully wrote borrower ID=$id to Firebase")
            borrowerDao.updateSyncStatus(id, SyncStatus.SYNCED)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "pushBorrower: Failed to write to Firebase: ${e.message}", e)
            throw e
        }
    }

    private suspend fun pushLoan(id: String): Boolean {
        Log.d(TAG, "pushLoan: Entry point for loan ID=$id")
        
        val firebaseDb = db
        if (firebaseDb == null) {
            Log.e(TAG, "pushLoan: Firebase database instance is null")
            return false
        }
        Log.d(TAG, "pushLoan: Firebase database instance OK")
        
        val entity = loanDao.getById(id)
        if (entity == null) {
            Log.i(TAG, "pushLoan: Entity ID=$id not found in local database (already deleted). Treating as done.")
            return true
        }
        Log.d(TAG, "pushLoan: Entity retrieved from local DB: borrowerID=${entity.borrowerId}, principal=${entity.principal}")
        
        val uid = ownerUid()
        if (uid == null) {
            Log.e(TAG, "pushLoan: User authentication UID is null (not authenticated)")
            return false
        }
        Log.d(TAG, "pushLoan: User UID=$uid")

        val path = "$ROOT/$uid/loans/${entity.id}"
        Log.d(TAG, "pushLoan: Writing to Firebase path: $path")
        
        val data = mapOf(
            "id"                   to entity.id,
            "ownerUid"             to uid,
            "borrowerId"           to entity.borrowerId,
            "principal"            to entity.principal,
            "interestAmount"       to entity.interestAmount,
            "loanDate"             to entity.loanDate.toString(),
            "firstDueDate"         to entity.firstDueDate.toString(),
            "numberOfInstallments" to entity.numberOfInstallments,
            "paymentFrequency"     to entity.paymentFrequency.name,
            "customFrequencyDays"  to entity.customFrequencyDays,
            "monthlyInterestAmount" to entity.monthlyInterestAmount,
            "latePenaltyAmount"    to entity.latePenaltyAmount,
            "latePenaltyGraceDays" to entity.latePenaltyGraceDays,
            "status"               to entity.status.name,
            "notes"                to entity.notes,
            "createdAt"            to entity.createdAt.toString(),
            "updatedAt"            to entity.updatedAt.toString(),
        )
        
        try {
            firebaseDb.getReference(path).setValue(data).await()
            Log.i(TAG, "pushLoan: Successfully wrote loan ID=$id to Firebase")

            // Also push all installments for this loan
            val installments = installmentDao.getByLoan(id)
            Log.d(TAG, "pushLoan: Pushing ${installments.size} installments for loan ID=$id")
            for (inst in installments) {
                val instData = mapOf(
                    "id"                to inst.id,
                    "loanId"            to inst.loanId,
                    "installmentNumber" to inst.installmentNumber,
                    "dueDate"           to inst.dueDate.toString(),
                    "amount"            to inst.amount,
                    "amountPaid"        to inst.amountPaid,
                )
                val instPath = "$ROOT/$uid/installments/${inst.loanId}/${inst.id}"
                firebaseDb.getReference(instPath).setValue(instData).await()
                Log.d(TAG, "pushLoan: Wrote installment ${inst.installmentNumber} to $instPath")
            }

            loanDao.updateSyncStatus(id, SyncStatus.SYNCED)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "pushLoan: Failed to write to Firebase: ${e.message}", e)
            throw e
        }
    }

    private suspend fun pushPayment(id: String): Boolean {
        Log.d(TAG, "pushPayment: Entry point for payment ID=$id")
        
        val firebaseDb = db
        if (firebaseDb == null) {
            Log.e(TAG, "pushPayment: Firebase database instance is null")
            return false
        }
        Log.d(TAG, "pushPayment: Firebase database instance OK")
        
        val entity = paymentDao.getById(id)
        if (entity == null) {
            Log.i(TAG, "pushPayment: Entity ID=$id not found in local database (already deleted). Treating as done.")
            return true
        }
        Log.d(TAG, "pushPayment: Entity retrieved from local DB: loanID=${entity.loanId}, amount=${entity.amount}")
        
        val uid = ownerUid()
        if (uid == null) {
            Log.e(TAG, "pushPayment: User authentication UID is null (not authenticated)")
            return false
        }
        Log.d(TAG, "pushPayment: User UID=$uid")

        val path = "$ROOT/$uid/payments/${entity.id}"
        Log.d(TAG, "pushPayment: Writing to Firebase path: $path")
        
        val data = mapOf(
            "id"              to entity.id,
            "ownerUid"        to uid,
            "loanId"          to entity.loanId,
            "borrowerId"      to entity.borrowerId,
            "amount"          to entity.amount,
            "paymentDate"     to entity.paymentDate.toString(),
            "paymentMethod"   to entity.paymentMethod.name,
            "referenceNumber" to entity.referenceNumber,
            "notes"           to entity.notes,
            "isReversed"      to entity.isReversed,
            "reversalReason"  to entity.reversalReason,
            "idempotencyKey"  to entity.idempotencyKey,
            "createdAt"       to entity.createdAt.toString(),
        )
        
        try {
            firebaseDb.getReference(path).setValue(data).await()
            Log.i(TAG, "pushPayment: Successfully wrote payment ID=$id to Firebase")

            // Update installment paid amounts on RTDB too
            val allocations = paymentDao.getAllocations(id)
            Log.d(TAG, "pushPayment: Updating ${allocations.size} installment allocations")
            for (alloc in allocations) {
                val inst = installmentDao.getById(alloc.installmentId)
                if (inst == null) {
                    Log.w(TAG, "pushPayment: Installment ${alloc.installmentId} not found, skipping")
                    continue
                }
                val instPath = "$ROOT/$uid/installments/${inst.loanId}/${inst.id}/amountPaid"
                firebaseDb.getReference(instPath).setValue(inst.amountPaid).await()
                Log.d(TAG, "pushPayment: Updated installment amountPaid at $instPath")
            }

            paymentDao.updateSyncStatus(id, SyncStatus.SYNCED)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "pushPayment: Failed to write to Firebase: ${e.message}", e)
            throw e
        }
    }

    // ── Delete operations ─────────────────────────────────────────────────────

    private suspend fun pushMonthlyCharge(id: String): Boolean {
        Log.d(TAG, "pushMonthlyCharge: Entry point for charge ID=$id")
        
        val firebaseDb = db
        if (firebaseDb == null) {
            Log.e(TAG, "pushMonthlyCharge: Firebase database instance is null")
            return false
        }
        
        val entity = monthlyInterestChargeDao.getById(id)
        if (entity == null) {
            Log.i(TAG, "pushMonthlyCharge: Entity ID=$id not found in local database (already deleted). Treating as done.")
            return true
        }
        Log.d(TAG, "pushMonthlyCharge: Entity retrieved from local DB: loanID=${entity.loanId}, amount=${entity.amount}")
        
        val uid = ownerUid()
        if (uid == null) {
            Log.e(TAG, "pushMonthlyCharge: User authentication UID is null (not authenticated)")
            return false
        }
        Log.d(TAG, "pushMonthlyCharge: User UID=$uid")

        val path = "$ROOT/$uid/monthly_charges/${entity.id}"
        Log.d(TAG, "pushMonthlyCharge: Writing to Firebase path: $path")
        
        val data = mapOf(
            "id"         to entity.id,
            "ownerUid"   to uid,
            "loanId"     to entity.loanId,
            "borrowerId" to entity.borrowerId,
            "amount"     to entity.amount,
            "chargeDate" to entity.chargeDate.toString(),
            "isPaid"     to entity.isPaid,
            "createdAt"  to entity.createdAt.toString(),
        )
        
        try {
            firebaseDb.getReference(path).setValue(data).await()
            Log.i(TAG, "pushMonthlyCharge: Successfully wrote charge ID=$id to Firebase")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "pushMonthlyCharge: Failed to write to Firebase: ${e.message}", e)
            throw e
        }
    }

    private suspend fun pushLatePenalty(id: String): Boolean {
        Log.d(TAG, "pushLatePenalty: Entry point for penalty ID=$id")
        
        val firebaseDb = db
        if (firebaseDb == null) {
            Log.e(TAG, "pushLatePenalty: Firebase database instance is null")
            return false
        }
        
        val entity = latePenaltyDao.getById(id)
        if (entity == null) {
            Log.i(TAG, "pushLatePenalty: Entity ID=$id not found in local database (already deleted). Treating as done.")
            return true
        }
        Log.d(TAG, "pushLatePenalty: Entity retrieved from local DB: loanID=${entity.loanId}, amount=${entity.amount}")
        
        val uid = ownerUid()
        if (uid == null) {
            Log.e(TAG, "pushLatePenalty: User authentication UID is null (not authenticated)")
            return false
        }
        Log.d(TAG, "pushLatePenalty: User UID=$uid")

        val path = "$ROOT/$uid/penalties/${entity.id}"
        Log.d(TAG, "pushLatePenalty: Writing to Firebase path: $path")
        
        val data = mapOf(
            "id"            to entity.id,
            "ownerUid"      to uid,
            "loanId"        to entity.loanId,
            "borrowerId"    to entity.borrowerId,
            "installmentId" to entity.installmentId,
            "amount"        to entity.amount,
            "penaltyDate"   to entity.penaltyDate.toString(),
            "reason"        to entity.reason,
            "isPaid"        to entity.isPaid,
            "createdAt"     to entity.createdAt.toString(),
        )
        
        try {
            firebaseDb.getReference(path).setValue(data).await()
            Log.i(TAG, "pushLatePenalty: Successfully wrote penalty ID=$id to Firebase")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "pushLatePenalty: Failed to write to Firebase: ${e.message}", e)
            throw e
        }
    }

    // ── Delete operations ─────────────────────────────────────────────────────

    private suspend fun deleteBorrowerFromFirebase(id: String): Boolean {
        val firebaseDb = db ?: return false
        val uid = ownerUid() ?: return false
        firebaseDb.getReference("$ROOT/$uid/borrowers/$id").removeValue().await()
        return true
    }

    private suspend fun deleteLoanFromFirebase(id: String): Boolean {
        val firebaseDb = db ?: return false
        val uid = ownerUid() ?: return false
        // Delete loan and all its installments
        firebaseDb.getReference("$ROOT/$uid/loans/$id").removeValue().await()
        firebaseDb.getReference("$ROOT/$uid/installments/$id").removeValue().await()
        return true
    }

    private suspend fun deletePaymentFromFirebase(id: String): Boolean {
        val firebaseDb = db ?: return false
        val uid = ownerUid() ?: return false

        // Load payment allocations to restore installment amountPaid values in Firebase
        val allocations = paymentDao.getAllocations(id)
        for (alloc in allocations) {
            val inst = installmentDao.getById(alloc.installmentId) ?: continue
            // Update the installment's amountPaid in Firebase to reflect the restoration
            firebaseDb.getReference("$ROOT/$uid/installments/${inst.loanId}/${inst.id}/amountPaid")
                .setValue(inst.amountPaid).await()
        }

        // Then remove the payment node
        firebaseDb.getReference("$ROOT/$uid/payments/$id").removeValue().await()
        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ownerUid(): String? = auth?.currentUser?.uid

    private fun isAuthenticated(): Boolean = auth?.currentUser != null
}
