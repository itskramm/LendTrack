package com.lendtrack.app.domain.calculation

import com.lendtrack.app.domain.model.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Simplified loan calculations using fixed interest amounts only.
 *
 * Interest is now a fixed amount (e.g., ₱150) rather than percentage-based.
 * Total payable = principal + interest amount
 * Each installment = total payable / number of installments
 *
 * NOTE: All monetary values are rounded to two decimal places using
 * half-up rounding. The last installment absorbs any residual rounding
 * difference to ensure the sum of all installments equals totalPayable exactly.
 */
object LoanCalculator {

    /**
     * Generate the installment schedule for a loan.
     * Uses fixed interest amount (no percentage calculations).
     */
    fun generateInstallments(
        loanId: String,
        principal: Double,
        interestAmount: Double,
        numberOfInstallments: Int,
        paymentFrequency: PaymentFrequency,
        customFrequencyDays: Int = 0,
        firstDueDate: LocalDate,
    ): List<Installment> {
        require(principal > 0)            { "Principal must be positive." }
        require(interestAmount >= 0)      { "Interest amount cannot be negative." }
        require(numberOfInstallments > 0) { "Number of installments must be positive." }

        val frequencyDays = if (paymentFrequency == PaymentFrequency.CUSTOM)
            customFrequencyDays else paymentFrequency.days
        require(frequencyDays > 0) { "Frequency days must be positive." }

        val totalPayable = principal + interestAmount
        val baseInstallmentAmount = round2(totalPayable / numberOfInstallments)

        return List(numberOfInstallments) { idx ->
            val amount = if (idx == numberOfInstallments - 1) {
                // Last installment absorbs rounding residual
                round2(totalPayable - baseInstallmentAmount * (numberOfInstallments - 1))
            } else {
                baseInstallmentAmount
            }

            Installment(
                id = UUID.randomUUID().toString(),
                loanId = loanId,
                installmentNumber = idx + 1,
                dueDate = firstDueDate.plusDays((idx * frequencyDays).toLong()),
                amount  = amount,
                amountPaid = 0.0,
                syncStatus = SyncStatus.PENDING,
            )
        }
    }

    /**
     * Apply a payment to a loan's installments using FIFO (First In, First Out).
     * Returns allocation records + updated installments.
     */
    fun applyPayment(
        paymentId: String,
        amount: Double,
        installments: List<Installment>,
    ): AllocationResult {
        require(amount > 0) { "Payment amount must be positive." }

        val allocations = mutableListOf<PaymentAllocation>()
        val updated = installments.toMutableList()
        var remaining = amount

        for (i in updated.indices) {
            if (remaining <= 0.0001) break  // Stop when payment fully allocated

            val inst = updated[i]
            val due = inst.remaining
            if (due <= 0.0001) continue  // Skip paid installments

            val allocated = minOf(remaining, due)
            allocations.add(
                PaymentAllocation(
                    id = UUID.randomUUID().toString(),
                    paymentId = paymentId,
                    installmentId = inst.id,
                    allocatedAmount = round2(allocated),
                )
            )
            updated[i] = inst.copy(amountPaid = round2(inst.amountPaid + allocated))
            remaining = round2(remaining - allocated)
        }

        return AllocationResult(
            allocations = allocations,
            updatedInstallments = updated,
            overpayment = maxOf(0.0, remaining),
        )
    }

    /** Round to 2 decimal places (half-up) */
    private fun round2(value: Double): Double =
        (value * 100.0).roundToLong() / 100.0
}

// ── Result types ──────────────────────────────────────────────────────────────

data class AllocationResult(
    val allocations: List<PaymentAllocation>,
    val updatedInstallments: List<Installment>,
    val overpayment: Double,
)
