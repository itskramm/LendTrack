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
import com.lendtrack.app.domain.calculation.AllocationResult
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
class PaymentRepository @Inject constructor(
    private val db:                       LendTrackDatabase,
    private val paymentDao:               PaymentDao,
    private val loanDao:                  LoanDao,
    private val installmentDao:           InstallmentDao,
    private val monthlyInterestChargeDao: MonthlyInterestChargeDao,
    private val latePenaltyDao:           LatePenaltyDao,
    private val syncDao:                  SyncDao,
    private val syncTrigger:              SyncTrigger,
) {
    // ── Observation ───────────────────────────────────────────────────────────

    fun observeAllPayments(): Flow<List<Payment>> =
        paymentDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observePaymentsByBorrower(borrowerId: String): Flow<List<Payment>> =
        paymentDao.observeByBorrower(borrowerId).map { list -> list.map { it.toDomain() } }

    fun observePaymentsByLoan(loanId: String): Flow<List<Payment>> =
        paymentDao.observeByLoan(loanId).map { list -> list.map { it.toDomain() } }

    fun observeCollectedThisMonth(monthPrefix: String): Flow<Double> =
        paymentDao.observeCollectedInMonth(monthPrefix)

    fun observeRecentPayments(limit: Int = 10): Flow<List<Payment>> =
        paymentDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getCollectedInRange(from: String, to: String): Double =
        paymentDao.getCollectedInRange(from, to)

    /**
     * Preview how a payment will be allocated before the user confirms.
     * Returns allocation details without writing anything to the database.
     * 
     * Allocation priority:
     * 1. Late penalties (oldest first)
     * 2. Monthly interest charges (oldest first)
     * 3. Installments (as currently implemented)
     */
    suspend fun previewPaymentAllocation(
        loanId:        String,
        paymentAmount: Double,
    ): PaymentPreview {
        val loan = loanDao.getById(loanId)
            ?: throw PaymentValidationException("Loan not found.")

        if (paymentAmount <= 0)
            throw PaymentValidationException("Payment amount must be greater than zero.")

        // Get all unpaid items
        val penalties = latePenaltyDao.getByLoan(loanId)
            .map { it.toDomainPenalty() }
            .filter { it.remaining > 0.005 }
            .sortedBy { it.penaltyDate }

        val charges = monthlyInterestChargeDao.getByLoan(loanId)
            .map { it.toDomainCharge() }
            .filter { it.remaining > 0.005 }
            .sortedBy { it.chargeDate }

        val installments = installmentDao.getByLoan(loanId)
            .map { it.toDomain() }
            .filter { it.remaining > 0.005 }

        val totalRemaining = penalties.sumOf { it.remaining } +
                           charges.sumOf { it.remaining } +
                           installments.sumOf { it.remaining }

        if (paymentAmount > totalRemaining + 0.005)
            throw PaymentValidationException(
                "Amount ₱${"%.2f".format(paymentAmount)} exceeds the loan balance of ₱${"%.2f".format(totalRemaining)}."
            )

        // Allocate payment following priority
        var remaining = paymentAmount
        val penaltyItems = mutableListOf<AllocationLineItem>()
        val chargeItems = mutableListOf<AllocationLineItem>()
        val installmentItems = mutableListOf<AllocationLineItem>()

        // 1. Apply to penalties first
        for (penalty in penalties) {
            if (remaining <= 0.005) break
            val allocatedAmount = minOf(remaining, penalty.remaining)
            penaltyItems.add(
                AllocationLineItem(
                    installmentNumber = 0, // penalties don't have installment numbers
                    dueDate           = penalty.penaltyDate,
                    allocatedAmount   = allocatedAmount,
                    remainingAfter    = penalty.remaining - allocatedAmount,
                    description       = "Late Penalty"
                )
            )
            remaining -= allocatedAmount
        }

        // 2. Apply to monthly charges
        for (charge in charges) {
            if (remaining <= 0.005) break
            val allocatedAmount = minOf(remaining, charge.remaining)
            chargeItems.add(
                AllocationLineItem(
                    installmentNumber = 0, // charges don't have installment numbers
                    dueDate           = charge.chargeDate,
                    allocatedAmount   = allocatedAmount,
                    remainingAfter    = charge.remaining - allocatedAmount,
                    description       = "Monthly Interest"
                )
            )
            remaining -= allocatedAmount
        }

        // 3. Apply to installments
        if (remaining > 0.005) {
            val domainInstallments = installments.filter { it.remaining > 0 }
            val result = LoanCalculator.applyPayment("preview", remaining, domainInstallments)
            
            result.allocations.forEach { alloc ->
                val inst = domainInstallments.first { it.id == alloc.installmentId }
                installmentItems.add(
                    AllocationLineItem(
                        installmentNumber = inst.installmentNumber,
                        dueDate           = inst.dueDate,
                        allocatedAmount   = alloc.allocatedAmount,
                        remainingAfter    = inst.remaining - alloc.allocatedAmount,
                        description       = "Installment #${inst.installmentNumber}"
                    )
                )
            }
        }

        return PaymentPreview(
            loanId           = loanId,
            paymentAmount    = paymentAmount,
            totalRemaining   = totalRemaining,
            balanceAfter     = totalRemaining - paymentAmount,
            penaltyItems     = penaltyItems,
            chargeItems      = chargeItems,
            installmentItems = installmentItems,
            overpayment      = maxOf(0.0, remaining),
        )
    }

    /**
     * Record a confirmed payment.
     *
     * Atomically:
     * 1. Insert the payment record.
     * 2. Apply payment following priority: penalties → charges → installments.
     * 3. Update amountPaid values for penalties, charges, and installments.
     * 4. Mark the loan as COMPLETED if balance is fully cleared.
     * 5. Insert a sync operation into the outbox.
     * 6. Insert an activity log entry.
     *
     * Idempotency: uses a unique idempotencyKey — if a key is reused, the
     * existing payment is returned without creating a duplicate.
     *
     * Returns the payment ID.
     */
    suspend fun recordPayment(
        loanId:          String,
        borrowerId:      String,
        amount:          Double,
        paymentDate:     LocalDate,
        paymentMethod:   PaymentMethod,
        referenceNumber: String = "",
        notes:           String = "",
        idempotencyKey:  String = UUID.randomUUID().toString(),
    ): String {
        // Idempotency guard
        val existing = paymentDao.getByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing.id

        // Validate
        if (amount <= 0)
            throw PaymentValidationException("Payment amount must be greater than zero.")

        val loan = loanDao.getById(loanId)
            ?: throw PaymentValidationException("Loan not found.")
        if (loan.status != LoanStatus.ACTIVE)
            throw PaymentValidationException("Cannot record a payment on a ${loan.status.name.lowercase()} loan.")

        // Get all unpaid items in priority order
        val penalties = latePenaltyDao.getByLoan(loanId)
            .map { it.toDomainPenalty() }
            .filter { it.remaining > 0.005 }
            .sortedBy { it.penaltyDate }

        val charges = monthlyInterestChargeDao.getByLoan(loanId)
            .map { it.toDomainCharge() }
            .filter { it.remaining > 0.005 }
            .sortedBy { it.chargeDate }

        val installments = installmentDao.getByLoan(loanId)
        val domainInstallments = installments.map { it.toDomain() }
        
        val totalRemaining = penalties.sumOf { it.remaining } +
                           charges.sumOf { it.remaining } +
                           domainInstallments.sumOf { it.remaining }

        if (amount > totalRemaining + 0.005)
            throw PaymentValidationException(
                "Amount ₱${"%.2f".format(amount)} exceeds the loan balance of ₱${"%.2f".format(totalRemaining)}."
            )

        val now       = LocalDateTime.now()
        val paymentId = UUID.randomUUID().toString()

        // Allocate payment following priority
        var remaining = amount
        val updatedPenalties = mutableListOf<LatePenalty>()
        val updatedCharges = mutableListOf<MonthlyInterestCharge>()
        var updatedInstallments = domainInstallments

        // 1. Apply to penalties first (oldest first)
        for (penalty in penalties) {
            if (remaining <= 0.005) break
            val allocatedAmount = minOf(remaining, penalty.remaining)
            val newAmountPaid = penalty.amountPaid + allocatedAmount
            val isPaid = newAmountPaid >= penalty.amount - 0.005
            
            updatedPenalties.add(
                penalty.copy(
                    amountPaid = newAmountPaid,
                    isPaid = isPaid
                )
            )
            remaining -= allocatedAmount
        }

        // 2. Apply to monthly charges (oldest first)
        for (charge in charges) {
            if (remaining <= 0.005) break
            val allocatedAmount = minOf(remaining, charge.remaining)
            val newAmountPaid = charge.amountPaid + allocatedAmount
            val isPaid = newAmountPaid >= charge.amount - 0.005
            
            updatedCharges.add(
                charge.copy(
                    amountPaid = newAmountPaid,
                    isPaid = isPaid
                )
            )
            remaining -= allocatedAmount
        }

        // 3. Apply to installments
        if (remaining > 0.005) {
            val allocationResult = LoanCalculator.applyPayment(paymentId, remaining, domainInstallments)
            updatedInstallments = allocationResult.updatedInstallments
        }

        // Build entities
        val paymentEntity = PaymentEntity(
            id             = paymentId,
            loanId         = loanId,
            borrowerId     = borrowerId,
            amount         = amount,
            paymentDate    = paymentDate,
            paymentMethod  = paymentMethod,
            referenceNumber = referenceNumber.trim(),
            notes          = notes.trim(),
            idempotencyKey = idempotencyKey,
            createdAt      = now,
            syncStatus     = SyncStatus.PENDING,
        )

        // Convert updated entities
        val penaltyEntities = updatedPenalties.map { p ->
            val original = latePenaltyDao.getById(p.id)!!
            original.copy(amountPaid = p.amountPaid, isPaid = p.isPaid)
        }

        val chargeEntities = updatedCharges.map { c ->
            val original = monthlyInterestChargeDao.getById(c.id)!!
            original.copy(amountPaid = c.amountPaid, isPaid = c.isPaid)
        }

        val installmentEntities = updatedInstallments.map { domain ->
            val original = installments.first { it.id == domain.id }
            original.copy(amountPaid = domain.amountPaid)
        }

        // Check if loan is now fully paid
        val newBalance = updatedInstallments.sumOf { it.remaining } +
                        updatedPenalties.sumOf { it.remaining } +
                        updatedCharges.sumOf { it.remaining }
        val loanCompleted = newBalance <= 0.005

        val syncOp = SyncOperationEntity(
            id             = UUID.randomUUID().toString(),
            operationType  = SyncOperationType.RECORD_PAYMENT,
            entityId       = paymentId,
            payload        = """{"paymentId":"$paymentId","loanId":"$loanId","borrowerId":"$borrowerId","amount":$amount,"date":"$paymentDate","method":"${paymentMethod.name}","idempotencyKey":"$idempotencyKey"}""",
            idempotencyKey = idempotencyKey,
            createdAt      = now,
        )

        val activity = ActivityLogEntity(
            id              = UUID.randomUUID().toString(),
            type            = ActivityType.PAYMENT,
            description     = "Payment of ₱${"%.2f".format(amount)} recorded — loan $loanId",
            relatedEntityId = paymentId,
            occurredAt      = now,
        )

        // Atomic write
        db.withTransaction {
            paymentDao.insertPayment(paymentEntity)
            
            if (penaltyEntities.isNotEmpty()) {
                latePenaltyDao.updateAll(penaltyEntities)
            }
            if (chargeEntities.isNotEmpty()) {
                monthlyInterestChargeDao.updateAll(chargeEntities)
            }
            
            installmentDao.updateAll(installmentEntities)
            
            if (loanCompleted) {
                loanDao.updateStatus(loanId, LoanStatus.COMPLETED, now.toString())
                syncDao.insertActivity(
                    ActivityLogEntity(
                        id          = UUID.randomUUID().toString(),
                        type        = ActivityType.SYSTEM,
                        description = "Loan $loanId marked as completed — fully paid",
                        relatedEntityId = loanId,
                        occurredAt  = now,
                    )
                )
            }
            syncDao.insertOperation(syncOp)
            syncDao.insertActivity(activity)
        }

        syncTrigger.onLocalWrite()
        return paymentId
    }

    /**
     * Reverse a payment — creates an auditable reversal record rather than
     * deleting the original.  Restores installment amountPaid values.
     *
     * Only non-reversed payments can be reversed.
     */
    suspend fun reversePayment(paymentId: String, reason: String) {
        if (reason.isBlank())
            throw PaymentValidationException("A reason is required to reverse a payment.")

        val payment = paymentDao.getById(paymentId)
            ?: throw PaymentValidationException("Payment not found.")
        if (payment.isReversed)
            throw PaymentValidationException("This payment has already been reversed.")

        val now = LocalDateTime.now()

        // Restore installment amounts
        val allocations = paymentDao.getAllocations(paymentId)
        allocations.forEach { alloc ->
            val inst = installmentDao.getById(alloc.installmentId) ?: return@forEach
            installmentDao.update(inst.copy(amountPaid = maxOf(0.0, inst.amountPaid - alloc.allocatedAmount)))
        }

        // Mark payment reversed
        paymentDao.updatePayment(payment.copy(isReversed = true, reversalReason = reason))

        // Re-activate loan if it was completed
        val loan = loanDao.getById(payment.loanId)
        if (loan?.status == LoanStatus.COMPLETED) {
            loanDao.updateStatus(payment.loanId, LoanStatus.ACTIVE, now.toString())
        }

        val syncOp = SyncOperationEntity(
            id             = UUID.randomUUID().toString(),
            operationType  = SyncOperationType.REVERSE_PAYMENT,
            entityId       = paymentId,
            payload        = """{"paymentId":"$paymentId","reason":"$reason"}""",
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt      = now,
        )

        val activity = ActivityLogEntity(
            id              = UUID.randomUUID().toString(),
            type            = ActivityType.ADJUSTMENT,
            description     = "Payment reversed — $reason",
            relatedEntityId = paymentId,
            occurredAt      = now,
        )

        syncDao.insertOperation(syncOp)
        syncDao.insertActivity(activity)
        syncTrigger.onLocalWrite()
    }

    /**
     * Delete a payment permanently — removes the payment record and restores
     * installment balances.  This operation cannot be undone.
     */
    suspend fun deletePayment(paymentId: String, reason: String) {
        if (reason.isBlank())
            throw PaymentValidationException("A reason is required to delete a payment.")

        val payment = paymentDao.getById(paymentId)
            ?: throw PaymentValidationException("Payment not found.")
        if (payment.isReversed)
            throw PaymentValidationException("Cannot delete a reversed payment. Delete the original payment instead.")

        val now = LocalDateTime.now()

        // Restore installment amounts and delete payment in transaction
        db.withTransaction {
            val allocations = paymentDao.getAllocations(paymentId)
            allocations.forEach { alloc ->
                val inst = installmentDao.getById(alloc.installmentId) ?: return@forEach
                installmentDao.update(inst.copy(amountPaid = maxOf(0.0, inst.amountPaid - alloc.allocatedAmount)))
            }

            // Re-activate loan if it was completed
            val loan = loanDao.getById(payment.loanId)
            if (loan?.status == LoanStatus.COMPLETED) {
                loanDao.updateStatus(payment.loanId, LoanStatus.ACTIVE, now.toString())
            }

            paymentDao.deleteAllocations(paymentId)
            paymentDao.deleteById(paymentId)

            syncDao.insertOperation(
                SyncOperationEntity(
                    id             = UUID.randomUUID().toString(),
                    operationType  = SyncOperationType.DELETE_PAYMENT,
                    entityId       = paymentId,
                    payload        = """{"paymentId":"$paymentId","reason":"$reason"}""",
                    idempotencyKey = UUID.randomUUID().toString(),
                    createdAt      = now,
                )
            )

            syncDao.insertActivity(
                ActivityLogEntity(
                    id              = UUID.randomUUID().toString(),
                    type            = ActivityType.ADJUSTMENT,
                    description     = "Payment deleted — ₱${"%.2f".format(payment.amount)} — $reason",
                    relatedEntityId = paymentId,
                    occurredAt      = now,
                )
            )
        }

        syncTrigger.onLocalWrite()
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun PaymentEntity.toDomain() = Payment(
        id              = id,
        loanId          = loanId,
        borrowerId      = borrowerId,
        amount          = amount,
        paymentDate     = paymentDate,
        paymentMethod   = paymentMethod,
        referenceNumber = referenceNumber,
        notes           = notes,
        isReversed      = isReversed,
        reversalReason  = reversalReason,
        idempotencyKey  = idempotencyKey,
        createdAt       = createdAt,
        syncStatus      = syncStatus,
        serverId        = serverId,
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

    private fun LatePenaltyEntity.toDomainPenalty() = LatePenalty(
        id            = id,
        loanId        = loanId,
        borrowerId    = borrowerId,
        installmentId = installmentId,
        amount        = amount,
        amountPaid    = amountPaid,
        penaltyDate   = penaltyDate,
        reason        = reason,
        isPaid        = isPaid,
        createdAt     = createdAt,
        syncStatus    = syncStatus,
    )

    private fun MonthlyInterestChargeEntity.toDomainCharge() = MonthlyInterestCharge(
        id         = id,
        loanId     = loanId,
        borrowerId = borrowerId,
        amount     = amount,
        amountPaid = amountPaid,
        chargeDate = chargeDate,
        isPaid     = isPaid,
        createdAt  = createdAt,
        syncStatus = syncStatus,
    )
}

// ── Payment preview models ────────────────────────────────────────────────────

data class PaymentPreview(
    val loanId:           String,
    val paymentAmount:    Double,
    val totalRemaining:   Double,
    val balanceAfter:     Double,
    val penaltyItems:     List<AllocationLineItem>,
    val chargeItems:      List<AllocationLineItem>,
    val installmentItems: List<AllocationLineItem>,
    val overpayment:      Double,
)

data class AllocationLineItem(
    val installmentNumber: Int,
    val dueDate:           java.time.LocalDate,
    val allocatedAmount:   Double,
    val remainingAfter:    Double,
    val description:       String = "Installment",
)

class PaymentValidationException(message: String) : Exception(message)
