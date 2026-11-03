package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.LoanEntity
import com.lendtrack.app.domain.model.LoanStatus
import com.lendtrack.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    fun observeActive(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    suspend fun getActive(): List<LoanEntity>

    @Query("SELECT * FROM loans ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE borrower_id = :borrowerId ORDER BY created_at DESC")
    fun observeByBorrower(borrowerId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE id = :id")
    fun observeById(id: String): Flow<LoanEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loan: LoanEntity)

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("UPDATE loans SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: LoanStatus, updatedAt: String)

    @Query("UPDATE loans SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus)

    @Query("UPDATE loans SET server_id = :serverId, sync_status = :status WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, status: SyncStatus = SyncStatus.SYNCED)

    @Query("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'")
    fun observeActiveLoanCount(): Flow<Int>

    /** Sum of (principal + interest) for all active loans */
    @Query("""
        SELECT COALESCE(SUM(principal + interest_amount), 0.0)
        FROM loans
        WHERE status = 'ACTIVE'
    """)
    fun observeTotalPayable(): Flow<Double>

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM loans WHERE borrower_id = :borrowerId")
    suspend fun deleteByBorrower(borrowerId: String)

    @Query("DELETE FROM loan_adjustments WHERE loan_id = :loanId")
    suspend fun deleteAdjustmentsByLoan(loanId: String)

    @Query("DELETE FROM loan_adjustments WHERE borrower_id = :borrowerId")
    suspend fun deleteAdjustmentsByBorrower(borrowerId: String)
}
