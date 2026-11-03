package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(installments: List<InstallmentEntity>)

    @Update
    suspend fun update(installment: InstallmentEntity)

    @Update
    suspend fun updateAll(installments: List<InstallmentEntity>)

    @Query("SELECT * FROM installments WHERE loan_id = :loanId ORDER BY installment_number ASC")
    fun observeByLoan(loanId: String): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE loan_id = :loanId ORDER BY installment_number ASC")
    suspend fun getByLoan(loanId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getById(id: String): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE due_date = :date ORDER BY due_date ASC")
    fun observeByDate(date: String): Flow<List<InstallmentEntity>>

    @Query("""
        SELECT * FROM installments
        WHERE due_date BETWEEN :from AND :to
        ORDER BY due_date ASC
    """)
    fun observeInRange(from: String, to: String): Flow<List<InstallmentEntity>>

    @Query("""
        SELECT * FROM installments
        WHERE due_date < :before
          AND (amount - amount_paid) > 0.001
        ORDER BY due_date ASC
    """)
    fun observeOverdue(before: String): Flow<List<InstallmentEntity>>

    /** Suspend version of overdue — used by notification worker */
    @Query("""
        SELECT * FROM installments
        WHERE due_date < :before
          AND (amount - amount_paid) > 0.001
        ORDER BY due_date ASC
    """)
    suspend fun getOverdueSuspend(before: String): List<InstallmentEntity>

    /** Installments due between two dates (today + tomorrow) — for due-soon notifications */
    @Query("""
        SELECT * FROM installments
        WHERE due_date BETWEEN :from AND :to
          AND (amount - amount_paid) > 0.001
        ORDER BY due_date ASC
    """)
    suspend fun getDueSoon(from: String, to: String): List<InstallmentEntity>

    @Query("""
        SELECT * FROM installments
        WHERE due_date = :today
          AND (amount - amount_paid) > 0.001
        ORDER BY installment_number ASC
    """)
    suspend fun getDueToday(today: String): List<InstallmentEntity>

    @Query("""
        SELECT COALESCE(SUM(amount - amount_paid), 0.0)
        FROM installments
        WHERE loan_id = :loanId
          AND (amount - amount_paid) > 0.001
    """)
    suspend fun getRemainingBalance(loanId: String): Double

    /** Sum of remaining balances per loan — used by LedgerViewModel for totalApplied */
    @Query("""
        SELECT COALESCE(SUM(amount_paid), 0.0)
        FROM installments
        WHERE loan_id = :loanId
    """)
    suspend fun getTotalPaid(loanId: String): Double

    @Query("""
        SELECT COALESCE(SUM(amount - amount_paid), 0.0)
        FROM installments
        WHERE (amount - amount_paid) > 0.001
    """)
    fun observeTotalOutstanding(): Flow<Double>

    @Query("DELETE FROM installments WHERE loan_id = :loanId")
    suspend fun deleteByLoan(loanId: String)

    @Query("DELETE FROM installments WHERE loan_id IN (SELECT id FROM loans WHERE borrower_id = :borrowerId)")
    suspend fun deleteByBorrower(borrowerId: String)
}
