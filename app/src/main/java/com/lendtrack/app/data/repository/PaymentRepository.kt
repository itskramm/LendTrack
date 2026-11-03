package com.lendtrack.app.data.repository

import androidx.room.withTransaction
import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.core.database.dao.LoanDao
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
    private val db:             LendTrackDatabase,
    private val paymentDao:     PaymentDao,
    private val loanDao:        LoanDao,
    private val installmentDao: InstallmentDao,
    private val syncDao:        SyncDao,
    private val syncTrigger:    SyncTrigger,
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
     */
    suspend fun previewPaymentAllocation(
        loanId:        String,
        paymentAmount: Double,
    ): PaymentPreview {
        val loan = loanDao.getById(loanId)
            ?: throw PaymentValidationException("Loan not found.")

        val installments = installmentDao.getByLoan(loanId)
        val domainInstallments = installments.map { it.toDomain() }
        val unpaid = domainInstallments.filter { it.remaining > 0 }

        val totalRemaining = unpaid.sumOf { it.remaining }

        if (paymentAmount <= 0)
            throw PaymentValidationException("Payment amount must be greater than zero.")
        if (paymentAmount > totalRemaining + 0.005)
            throw PaymentValidationException(
                "Amount ₱${"%.2f".format(paymentAmount)} exceeds the loan balance of ₱${"%.2f".format(totalRemaining)}."
            )

        val result = LoanCalculator.applyPayment("preview", paymentAmount, domainInstallments)

        val lineItems = result.allocations.map { alloc ->
            val inst = domainInstallments.first { it.id == alloc.installmentId }
            AllocationLineItem(
                installmentNumber = inst.installmentNumber,
                dueDate           = inst.dueDate,
                allocatedAmount   = alloc.allocatedAmount,
                remainingAfter    = inst.remaining - alloc.allocatedAmount,
            )
        }

        return PaymentPreview(
            loanId           = loanId,
            paymentAmount    = paymentAmount,
            totalRemaining   = totalRemaining,
            balanceAfter     = totalRemaining - paymentAmount,
            allocationItems  = lineItems,
            overpayment      = result.overpayment,
        )
    }

    /**
     * Record a confirmed payment.
     *
     * Atomically:
     * 1. Insert the payment record.
     * 2. Insert payment allocation records (one per installment touched).
     * 3. Update installment amountPaid values.
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

        val installments = installmentDao.getByLoan(loanId)
        val domainInstallments = installments.map { it.toDomain() }
        val totalRemaining = domainInstallments.sumOf { it.remaining }

        if (amount > totalRemaining + 0.005)
            throw PaymentValidationException(
                "Amount ₱${"%.2f".format(amount)} exceeds the loan balance of ₱${"%.2f".format(totalRemaining)}."
            )

        val now       = LocalDateTime.now()
        val paymentId = UUID.randomUUID().toString()

        val allocationResult = LoanCalculator.applyPayment(paymentId, amount, domainInstallments)

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

        val allocationEntities = allocationResult.allocations.map { alloc ->
            PaymentAllocationEntity(
                id              = alloc.id,
                paymentId       = paymentId,
                installmentId   = alloc.installmentId,
                allocatedAmount = alloc.allocatedAmount,
                createdAt       = now,
            )
        }

        val updatedInstallmentEntities = allocationResult.updatedInstallments.map { domain ->
            val original = installments.first { it.id == domain.id }
            original.copy(amountPaid = domain.amountPaid)
        }

        // Check if loan is now fully paid
        val newBalance = allocationResult.updatedInstallments.sumOf { it.remaining }
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
            paymentDao.insertAllocations(allocationEntities)
            installmentDao.updateAll(updatedInstallmentEntities)
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
}

// ── Payment preview models ────────────────────────────────────────────────────

data class PaymentPreview(
    val loanId:          String,
    val paymentAmount:   Double,
    val totalRemaining:  Double,
    val balanceAfter:    Double,
    val allocationItems: List<AllocationLineItem>,
    val overpayment:     Double,
)

data class AllocationLineItem(
    val installmentNumber: Int,
    val dueDate:           java.time.LocalDate,
    val allocatedAmount:   Double,
    val remainingAfter:    Double,
)

class PaymentValidationException(message: String) : Exception(message)
