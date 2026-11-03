package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.PaymentAllocationEntity
import com.lendtrack.app.core.database.entities.PaymentEntity
import com.lendtrack.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<PaymentAllocationEntity>)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments ORDER BY payment_date DESC, created_at DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("""
        SELECT * FROM payments
        WHERE borrower_id = :borrowerId
          AND is_reversed = 0
        ORDER BY payment_date DESC
    """)
    fun observeByBorrower(borrowerId: String): Flow<List<PaymentEntity>>

    @Query("""
        SELECT * FROM payments
        WHERE loan_id = :loanId
          AND is_reversed = 0
        ORDER BY payment_date DESC
    """)
    fun observeByLoan(loanId: String): Flow<List<PaymentEntity>>

    @Query("""
        SELECT * FROM payments
        WHERE loan_id = :loanId
        ORDER BY payment_date ASC, created_at ASC
    """)
    suspend fun getByLoan(loanId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payment_allocations WHERE payment_id = :paymentId")
    suspend fun getAllocations(paymentId: String): List<PaymentAllocationEntity>

    /** Total collected in a given month (YYYY-MM prefix) */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM payments
        WHERE payment_date LIKE :monthPrefix || '%'
          AND is_reversed = 0
    """)
    fun observeCollectedInMonth(monthPrefix: String): Flow<Double>

    /** Total collected in a date range */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM payments
        WHERE payment_date BETWEEN :from AND :to
          AND is_reversed = 0
    """)
    suspend fun getCollectedInRange(from: String, to: String): Double

    @Query("SELECT * FROM payments WHERE sync_status = :status")
    suspend fun getPending(status: SyncStatus = SyncStatus.PENDING): List<PaymentEntity>

    @Query("UPDATE payments SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE payments SET server_id = :serverId, sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String)

    /** Idempotency check — return existing payment if idempotency key is reused */
    @Query("SELECT * FROM payments WHERE idempotency_key = :key LIMIT 1")
    suspend fun getByIdempotencyKey(key: String): PaymentEntity?

    /** Recent payments for dashboard feed */
    @Query("""
        SELECT * FROM payments
        WHERE is_reversed = 0
        ORDER BY payment_date DESC, created_at DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<PaymentEntity>>

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payments WHERE loan_id = :loanId")
    suspend fun deleteByLoan(loanId: String)

    @Query("DELETE FROM payments WHERE borrower_id = :borrowerId")
    suspend fun deleteByBorrower(borrowerId: String)

    @Query("DELETE FROM payment_allocations WHERE payment_id = :paymentId")
    suspend fun deleteAllocations(paymentId: String)

    @Query("DELETE FROM payment_allocations WHERE payment_id IN (SELECT id FROM payments WHERE loan_id = :loanId)")
    suspend fun deleteAllocationsByLoan(loanId: String)

    @Query("DELETE FROM payment_allocations WHERE payment_id IN (SELECT id FROM payments WHERE borrower_id = :borrowerId)")
    suspend fun deleteAllocationsByBorrower(borrowerId: String)
}
