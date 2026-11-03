package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.LatePenaltyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LatePenaltyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(penalty: LatePenaltyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(penalties: List<LatePenaltyEntity>)

    @Update
    suspend fun update(penalty: LatePenaltyEntity)

    @Query("SELECT * FROM late_penalties WHERE id = :id")
    suspend fun getById(id: String): LatePenaltyEntity?

    @Query("SELECT * FROM late_penalties WHERE loan_id = :loanId ORDER BY penalty_date DESC")
    suspend fun getByLoan(loanId: String): List<LatePenaltyEntity>

    @Query("SELECT * FROM late_penalties WHERE loan_id = :loanId ORDER BY penalty_date DESC")
    fun observeByLoan(loanId: String): Flow<List<LatePenaltyEntity>>

    @Query("SELECT * FROM late_penalties WHERE borrower_id = :borrowerId ORDER BY penalty_date DESC")
    suspend fun getByBorrower(borrowerId: String): List<LatePenaltyEntity>

    @Query("SELECT * FROM late_penalties WHERE installment_id = :installmentId")
    suspend fun getByInstallment(installmentId: String): List<LatePenaltyEntity>

    @Query("""
        SELECT * FROM late_penalties 
        WHERE installment_id = :installmentId
        LIMIT 1
    """)
    suspend fun getPenaltyForInstallment(installmentId: String): LatePenaltyEntity?

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM late_penalties
        WHERE loan_id = :loanId
          AND is_paid = 0
    """)
    suspend fun getUnpaidTotal(loanId: String): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM late_penalties
        WHERE is_paid = 0
    """)
    fun observeTotalUnpaid(): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM late_penalties
        WHERE is_paid = 0
    """)
    suspend fun getTotalUnpaid(): Double

    @Query("UPDATE late_penalties SET is_paid = :isPaid WHERE id = :id")
    suspend fun updatePaidStatus(id: String, isPaid: Boolean)

    @Query("DELETE FROM late_penalties WHERE loan_id = :loanId")
    suspend fun deleteByLoan(loanId: String)

    @Query("DELETE FROM late_penalties WHERE borrower_id = :borrowerId")
    suspend fun deleteByBorrower(borrowerId: String)

    @Query("""
        SELECT * FROM late_penalties
        WHERE is_paid = 0
        ORDER BY penalty_date ASC
    """)
    suspend fun getAllUnpaid(): List<LatePenaltyEntity>
}
