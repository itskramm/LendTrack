package com.lendtrack.app.data.repository

import androidx.room.withTransaction
import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.core.database.dao.LatePenaltyDao
import com.lendtrack.app.core.database.dao.LoanDao
import com.lendtrack.app.core.database.dao.MonthlyInterestChargeDao
import com.lendtrack.app.core.database.dao.PaymentDao
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.*
import com.lendtrack.app.core.sync.SyncTrigger
import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val db:                      LendTrackDatabase,
    private val loanDao:                 LoanDao,
    private val installmentDao:          InstallmentDao,
    private val paymentDao:              PaymentDao,
    private val syncDao:                 SyncDao,
    private val monthlyInterestChargeDao: MonthlyInterestChargeDao,
    private val latePenaltyDao:          LatePenaltyDao,
    private val syncTrigger:             SyncTrigger,
) {
    // ── Observation ───────────────────────────────────────────────────────────

    fun observeActiveLoans(): Flow<List<Loan>> =
        loanDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAllLoans(): Flow<List<Loan>> =
        loanDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeLoansByBorrower(borrowerId: String): Flow<List<Loan>> =
        loanDao.observeByBorrower(borrowerId).map { list -> list.map { it.toDomain() } }

    fun observeLoan(id: String): Flow<Loan?> =
        loanDao.observeById(id).map { it?.toDomain() }

    fun observeInstallments(loanId: String): Flow<List<Installment>> =
        installmentDao.observeByLoan(loanId).map { list -> list.map { it.toDomain() } }

    fun observeInstallmentsInRange(from: String, to: String): Flow<List<Installment>> =
        installmentDao.observeInRange(from, to).map { list -> list.map { it.toDomain() } }

    fun observeOverdueInstallments(before: String): Flow<List<Installment>> =
        installmentDao.observeOverdue(before).map { list -> list.map { it.toDomain() } }

    fun observeTotalOutstanding(): Flow<Double> =
        installmentDao.observeTotalOutstanding()

    fun observeActiveLoanCount(): Flow<Int> =
        loanDao.observeActiveLoanCount()

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Create a new loan and generate its complete installment schedule.
     *
     * All four writes (loan entity, installments, sync operation, activity log)
     * execute in a single Room transaction — either all succeed or all roll back.
     *
     * Interest is now a fixed amount (e.g., ₱150) rather than percentage-based.
     *
     * Returns the new loan's local ID.
     */
    suspend fun createLoan(
        borrowerId:            String,
        principal:             Double,
        interestAmount:        Double,
        loanDate:              LocalDate,
        firstDueDate:          LocalDate,
        numberOfInstallments:  Int,
        paymentFrequency:      PaymentFrequency,
        customFrequencyDays:   Int = 0,
        monthlyInterestAmount: Double = 0.0,
        latePenaltyAmount:     Double = 0.0,
        latePenaltyGraceDays:  Int = 0,
        notes:                 String = "",
    ): String {
        if (principal <= 0)            throw LoanValidationException("Principal must be a positive amount.")
        if (interestAmount < 0)        throw LoanValidationException("Interest amount cannot be negative.")
        if (numberOfInstallments <= 0) throw LoanValidationException("Number of installments must be at least 1.")
        if (!firstDueDate.isAfter(loanDate.minusDays(1))) {
            throw LoanValidationException("First due date must be on or after the loan date.")
        }
        if (paymentFrequency == PaymentFrequency.CUSTOM && customFrequencyDays <= 0) {
            throw LoanValidationException("Custom frequency days must be positive.")
        }

        val totalPayable = principal + interestAmount

        val now    = LocalDateTime.now()
        val loanId = UUID.randomUUID().toString()

        val loanEntity = LoanEntity(
            id                    = loanId,
            borrowerId            = borrowerId,
            principal             = principal,
            interestAmount        = interestAmount,
            loanDate              = loanDate,
            firstDueDate          = firstDueDate,
            numberOfInstallments  = numberOfInstallments,
            paymentFrequency      = paymentFrequency,
            customFrequencyDays   = customFrequencyDays,
            monthlyInterestAmount = monthlyInterestAmount,
            latePenaltyAmount     = latePenaltyAmount,
            latePenaltyGraceDays  = latePenaltyGraceDays,
            status                = LoanStatus.ACTIVE,
            notes                 = notes.trim(),
            createdAt             = now,
            updatedAt             = now,
            syncStatus            = SyncStatus.PENDING,
        )

        val installmentEntities = LoanCalculator.generateInstallments(
            loanId               = loanId,
            principal            = principal,
            interestAmount       = interestAmount,
            numberOfInstallments = numberOfInstallments,
            paymentFrequency     = paymentFrequency,
            customFrequencyDays  = customFrequencyDays,
            firstDueDate         = firstDueDate,
        ).map { inst ->
            InstallmentEntity(
                id                = inst.id,
                loanId            = loanId,
                installmentNumber = inst.installmentNumber,
                dueDate           = inst.dueDate,
                amount            = inst.amount,
                amountPaid        = 0.0,
                syncStatus        = SyncStatus.PENDING,
            )
        }

        val syncOp = SyncOperationEntity(
            id             = UUID.randomUUID().toString(),
            operationType  = SyncOperationType.CREATE_LOAN,
            entityId       = loanId,
            payload        = """{"id":"$loanId","borrowerId":"$borrowerId","principal":$principal,"interest":$interestAmount,"totalPayable":$totalPayable,"installments":$numberOfInstallments,"frequency":"${paymentFrequency.name}"}""",
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt      = now,
        )
        val activity = ActivityLogEntity(
            id              = UUID.randomUUID().toString(),
            type            = ActivityType.NEW_LOAN,
            description     = "New loan created — ₱${"%.2f".format(principal)} principal for borrower $borrowerId",
            relatedEntityId = loanId,
            occurredAt      = now,
        )

        // Single atomic transaction — loan + schedule + outbox entry + activity log
        db.withTransaction {
            loanDao.insert(loanEntity)
            installmentDao.insertAll(installmentEntities)
            syncDao.insertOperation(syncOp)
            syncDao.insertActivity(activity)
        }

        syncTrigger.onLocalWrite()
        return loanId
    }

    /**
     * Update an active loan with new parameters.
     * Regenerates installments and re-applies existing payments FIFO.
     * All mutations happen in a single Room transaction.
     */
    suspend fun updateLoan(
        loanId:                String,
        principal:             Double,
        interestAmount:        Double,
        loanDate:              LocalDate,
        firstDueDate:          LocalDate,
        numberOfInstallments:  Int,
        paymentFrequency:      PaymentFrequency,
        customFrequencyDays:   Int = 0,
        monthlyInterestAmount: Double = 0.0,
        latePenaltyAmount:     Double = 0.0,
        latePenaltyGraceDays:  Int = 0,
        notes:                 String = "",
    ) {
        // ── Step 3: Input validation ──────────────────────────────────────────
        if (principal <= 0)            throw LoanValidationException("Principal must be a positive amount.")
        if (interestAmount < 0)        throw LoanValidationException("Interest amount cannot be negative.")
        if (numberOfInstallments <= 0) throw LoanValidationException("Number of installments must be at least 1.")
        if (!firstDueDate.isAfter(loanDate.minusDays(1))) {
            throw LoanValidationException("First due date must be on or after the loan date.")
        }
        if (paymentFrequency == PaymentFrequency.CUSTOM && customFrequencyDays <= 0) {
            throw LoanValidationException("Custom frequency days must be positive.")
        }

        val existing = loanDao.getById(loanId)
            ?: throw LoanValidationException("Loan not found.")
        if (existing.status != LoanStatus.ACTIVE) {
            throw LoanValidationException("Only active loans can be edited.")
        }

        // ── Step 4: Payment preservation validation ───────────────────────────
        val allPayments = paymentDao.getByLoan(loanId)
        val validPayments = allPayments.filter { !it.isReversed }
        val totalPaid = validPayments.sumOf { it.amount }
        val newTotalPayable = principal + interestAmount

        if (totalPaid > newTotalPayable + 0.01) {
            throw LoanValidationException(
                "Cannot reduce loan below already-paid amount. " +
                "Total paid: ₱${"%.2f".format(totalPaid)}, " +
                "new total: ₱${"%.2f".format(newTotalPayable)}."
            )
        }

        val now = LocalDateTime.now()

        // ── Step 5: Transaction structure ─────────────────────────────────────
        db.withTransaction {
            // (a) Update loan entity
            val updatedLoan = existing.copy(
                principal             = principal,
                interestAmount        = interestAmount,
                loanDate              = loanDate,
                firstDueDate          = firstDueDate,
                numberOfInstallments  = numberOfInstallments,
                paymentFrequency      = paymentFrequency,
                customFrequencyDays   = customFrequencyDays,
                monthlyInterestAmount = monthlyInterestAmount,
                latePenaltyAmount     = latePenaltyAmount,
                latePenaltyGraceDays  = latePenaltyGraceDays,
                notes                 = notes.trim(),
                updatedAt             = now,
                syncStatus            = SyncStatus.PENDING,
            )
            loanDao.update(updatedLoan)

            // (b) Delete all existing installments
            installmentDao.deleteByLoan(loanId)

            // (c) Generate new installments
            val newInstallments = LoanCalculator.generateInstallments(
                loanId               = loanId,
                principal            = principal,
                interestAmount       = interestAmount,
                numberOfInstallments = numberOfInstallments,
                paymentFrequency     = paymentFrequency,
                customFrequencyDays  = customFrequencyDays,
                firstDueDate         = firstDueDate,
            ).map { inst ->
                InstallmentEntity(
                    id                = inst.id,
                    loanId            = loanId,
                    installmentNumber = inst.installmentNumber,
                    dueDate           = inst.dueDate,
                    amount            = inst.amount,
                    amountPaid        = 0.0,
                    syncStatus        = SyncStatus.PENDING,
                )
            }

            // (d) Insert new installments
            installmentDao.insertAll(newInstallments)

            // ── Step 6: Payment re-application ────────────────────────────────
            // Sort payments by date, then creation time (FIFO)
            val sortedPayments = validPayments.sortedWith(
                compareBy({ it.paymentDate }, { it.createdAt })
            )

            for (payment in sortedPayments) {
                // Load current installment states
                val currentInstallments = installmentDao.getByLoan(loanId).map { entity ->
                    Installment(
                        id                = entity.id,
                        loanId            = entity.loanId,
                        installmentNumber = entity.installmentNumber,
                        dueDate           = entity.dueDate,
                        amount            = entity.amount,
                        amountPaid        = entity.amountPaid,
                        syncStatus        = entity.syncStatus,
                    )
                }

                // Apply payment using LoanCalculator
                val result = LoanCalculator.applyPayment(
                    paymentId    = payment.id,
                    amount       = payment.amount,
                    installments = currentInstallments,
                )

                // Check for overpayment
                if (result.overpayment > 0.01) {
                    throw LoanValidationException(
                        "After edit, payment ${payment.id} overpays by ₱${"%.2f".format(result.overpayment)}. " +
                        "Reduce principal or increase installments."
                    )
                }

                // Delete old allocations for this payment
                paymentDao.deleteAllocations(payment.id)

                // Insert new allocations
                val allocationEntities = result.allocations.map { alloc ->
                    PaymentAllocationEntity(
                        id              = alloc.id,
                        paymentId       = alloc.paymentId,
                        installmentId   = alloc.installmentId,
                        allocatedAmount = alloc.allocatedAmount,
                        createdAt       = now,
                    )
                }
                paymentDao.insertAllocations(allocationEntities)

                // Update installment amountPaid values
                val updatedInstallmentEntities = result.updatedInstallments.map { inst ->
                    InstallmentEntity(
                        id                = inst.id,
                        loanId            = inst.loanId,
                        installmentNumber = inst.installmentNumber,
                        dueDate           = inst.dueDate,
                        amount            = inst.amount,
                        amountPaid        = inst.amountPaid,
                        syncStatus        = SyncStatus.PENDING,
                    )
                }
                installmentDao.updateAll(updatedInstallmentEntities)
            }

            // ── Step 7: Sync operation and activity log ───────────────────────
            val syncOp = SyncOperationEntity(
                id             = UUID.randomUUID().toString(),
                operationType  = SyncOperationType.UPDATE_LOAN,
                entityId       = loanId,
                payload        = """{"id":"$loanId","principal":$principal,"interestAmount":$interestAmount,"numberOfInstallments":$numberOfInstallments,"frequency":"${paymentFrequency.name}"}""",
                idempotencyKey = UUID.randomUUID().toString(),
                createdAt      = now,
            )
            syncDao.insertOperation(syncOp)

            val activity = ActivityLogEntity(
                id              = UUID.randomUUID().toString(),
                type            = ActivityType.ADJUSTMENT,
                description     = "Loan edited — ₱${"%.2f".format(principal)} principal, $numberOfInstallments installments",
                relatedEntityId = loanId,
                occurredAt      = now,
            )
            syncDao.insertActivity(activity)
        }

        // Trigger sync after transaction completes
        syncTrigger.onLocalWrite()
    }

    /**
     * Cancel an active loan. Cannot be undone.
     */
    suspend fun cancelLoan(loanId: String, reason: String) {
        val existing = loanDao.getById(loanId)
            ?: throw LoanValidationException("Loan not found.")
        if (existing.status != LoanStatus.ACTIVE) {
            throw LoanValidationException("Only active loans can be cancelled.")
        }

        val now = LocalDateTime.now()
        loanDao.updateStatus(loanId, LoanStatus.CANCELLED, now.toString())
        syncDao.insertOperation(
            SyncOperationEntity(
                id             = UUID.randomUUID().toString(),
                operationType  = SyncOperationType.CANCEL_LOAN,
                entityId       = loanId,
                payload        = """{"id":"$loanId","reason":"$reason"}""",
                idempotencyKey = UUID.randomUUID().toString(),
                createdAt      = now,
            )
        )
        syncTrigger.onLocalWrite()
    }

    /**
     * Delete a loan permanently along with all its installments.
     * This operation cannot be undone.
     */
    suspend fun deleteLoan(loanId: String, reason: String) {
        if (reason.isBlank())
            throw LoanValidationException("A reason is required to delete a loan.")

        val existing = loanDao.getById(loanId)
            ?: throw LoanValidationException("Loan not found.")

        val now = LocalDateTime.now()

        // Delete in transaction: allocations, payments, adjustments, installments, then loan
        db.withTransaction {
            paymentDao.deleteAllocationsByLoan(loanId)
            paymentDao.deleteByLoan(loanId)
            loanDao.deleteAdjustmentsByLoan(loanId)
            installmentDao.deleteByLoan(loanId)
            loanDao.deleteById(loanId)

            syncDao.insertOperation(
                SyncOperationEntity(
                    id             = UUID.randomUUID().toString(),
                    operationType  = SyncOperationType.DELETE_LOAN,
                    entityId       = loanId,
                    payload        = """{"id":"$loanId","reason":"$reason"}""",
                    idempotencyKey = UUID.randomUUID().toString(),
                    createdAt      = now,
                )
            )

            syncDao.insertActivity(
                ActivityLogEntity(
                    id              = UUID.randomUUID().toString(),
                    type            = ActivityType.ADJUSTMENT,
                    description     = "Loan deleted — ₱${"%.2f".format(existing.principal)} — $reason",
                    relatedEntityId = loanId,
                    occurredAt      = now,
                )
            )
        }

        syncTrigger.onLocalWrite()
    }

    suspend fun getRemainingBalance(loanId: String): Double =
        installmentDao.getRemainingBalance(loanId)

    /**
     * Get the complete balance breakdown for a loan including:
     * - Remaining installment balance
     * - Unpaid monthly interest charges
     * - Unpaid late penalties
     */
    suspend fun getTotalBalance(loanId: String): LoanBalance {
        val installmentBalance = installmentDao.getRemainingBalance(loanId)
        val monthlyCharges = monthlyInterestChargeDao.getUnpaidTotal(loanId)
        val penalties = latePenaltyDao.getUnpaidTotal(loanId)
        
        return LoanBalance(
            installmentBalance = installmentBalance,
            monthlyCharges = monthlyCharges,
            penalties = penalties,
        )
    }

    /**
     * Get all monthly interest charges for a loan.
     */
    suspend fun getMonthlyCharges(loanId: String): List<MonthlyInterestCharge> =
        monthlyInterestChargeDao.getByLoan(loanId).map { it.toDomain() }

    /**
     * Get all late penalties for a loan.
     */
    suspend fun getLatePenalties(loanId: String): List<LatePenalty> =
        latePenaltyDao.getByLoan(loanId).map { it.toDomain() }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun LoanEntity.toDomain() = Loan(
        id                    = id,
        borrowerId            = borrowerId,
        principal             = principal,
        interestAmount        = interestAmount,
        loanDate              = loanDate,
        firstDueDate          = firstDueDate,
        numberOfInstallments  = numberOfInstallments,
        paymentFrequency      = paymentFrequency,
        customFrequencyDays   = customFrequencyDays,
        monthlyInterestAmount = monthlyInterestAmount,
        latePenaltyAmount     = latePenaltyAmount,
        latePenaltyGraceDays  = latePenaltyGraceDays,
        status                = status,
        notes                 = notes,
        createdAt             = createdAt,
        updatedAt             = updatedAt,
        syncStatus            = syncStatus,
        serverId              = serverId,
    )

    private fun InstallmentEntity.toDomain() = Installment(
        id                = id,
        loanId            = loanId,
        installmentNumber = installmentNumber,
        dueDate           = dueDate,
        amount            = amount,
        amountPaid        = amountPaid,
        syncStatus        = syncStatus,
    )

    private fun MonthlyInterestChargeEntity.toDomain() = MonthlyInterestCharge(
        id         = id,
        loanId     = loanId,
        borrowerId = borrowerId,
        amount     = amount,
        chargeDate = chargeDate,
        isPaid     = isPaid,
        createdAt  = createdAt,
        syncStatus = syncStatus,
    )

    private fun LatePenaltyEntity.toDomain() = LatePenalty(
        id            = id,
        loanId        = loanId,
        borrowerId    = borrowerId,
        installmentId = installmentId,
        amount        = amount,
        penaltyDate   = penaltyDate,
        reason        = reason,
        isPaid        = isPaid,
        createdAt     = createdAt,
        syncStatus    = syncStatus,
    )
}

class LoanValidationException(message: String) : Exception(message)
