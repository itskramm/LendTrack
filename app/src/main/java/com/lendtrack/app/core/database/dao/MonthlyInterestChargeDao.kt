package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.MonthlyInterestChargeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyInterestChargeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(charge: MonthlyInterestChargeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(charges: List<MonthlyInterestChargeEntity>)

    @Update
    suspend fun update(charge: MonthlyInterestChargeEntity)

    @Update
    suspend fun updateAll(charges: List<MonthlyInterestChargeEntity>)

    @Query("SELECT * FROM monthly_interest_charges WHERE id = :id")
    suspend fun getById(id: String): MonthlyInterestChargeEntity?

    @Query("SELECT * FROM monthly_interest_charges WHERE loan_id = :loanId ORDER BY charge_date DESC")
    suspend fun getByLoan(loanId: String): List<MonthlyInterestChargeEntity>

    @Query("SELECT * FROM monthly_interest_charges WHERE loan_id = :loanId ORDER BY charge_date DESC")
    fun observeByLoan(loanId: String): Flow<List<MonthlyInterestChargeEntity>>

    @Query("SELECT * FROM monthly_interest_charges WHERE borrower_id = :borrowerId ORDER BY charge_date DESC")
    suspend fun getByBorrower(borrowerId: String): List<MonthlyInterestChargeEntity>

    @Query("""
        SELECT * FROM monthly_interest_charges 
        WHERE loan_id = :loanId 
          AND strftime('%Y-%m', charge_date) = :yearMonth
        LIMIT 1
    """)
    suspend fun getChargeForMonth(loanId: String, yearMonth: String): MonthlyInterestChargeEntity?

    @Query("""
        SELECT COALESCE(SUM(amount - amount_paid), 0.0)
        FROM monthly_interest_charges
        WHERE loan_id = :loanId
    """)
    suspend fun getUnpaidTotal(loanId: String): Double

    @Query("""
        SELECT COALESCE(SUM(amount - amount_paid), 0.0)
        FROM monthly_interest_charges
    """)
    fun observeTotalUnpaid(): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount - amount_paid), 0.0)
        FROM monthly_interest_charges
    """)
    suspend fun getTotalUnpaid(): Double

    @Query("UPDATE monthly_interest_charges SET is_paid = :isPaid WHERE id = :id")
    suspend fun updatePaidStatus(id: String, isPaid: Boolean)

    @Query("DELETE FROM monthly_interest_charges WHERE loan_id = :loanId")
    suspend fun deleteByLoan(loanId: String)

    @Query("DELETE FROM monthly_interest_charges WHERE borrower_id = :borrowerId")
    suspend fun deleteByBorrower(borrowerId: String)

    @Query("""
        SELECT * FROM monthly_interest_charges
        WHERE amount - amount_paid > 0.01
        ORDER BY charge_date ASC
    """)
    suspend fun getAllUnpaid(): List<MonthlyInterestChargeEntity>
}
