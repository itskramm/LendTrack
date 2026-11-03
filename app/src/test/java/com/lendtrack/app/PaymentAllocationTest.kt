package com.lendtrack.app

import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.InstallmentStatus
import com.lendtrack.app.domain.model.PaymentFrequency
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for payment allocation rules and installment status transitions.
 */
class PaymentAllocationTest {

    private val today = LocalDate.of(2025, 10, 12)
    private val future = today.plusDays(14)

    // ── FIFO allocation ───────────────────────────────────────────────────────

    @Test
    fun `allocation always targets oldest unpaid installment first`() {
        // 3 installments, first already partially paid
        val base = LoanCalculator.generateInstallments(
            loanId               = "FIFO-1",
            principal            = 6_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 3,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today.minusDays(28),
        )
        // Partially pay first installment externally
        val withPartial = base.mapIndexed { i, inst ->
            if (i == 0) inst.copy(amountPaid = 1_000.0) else inst
        }
        val result = LoanCalculator.applyPayment("P-FIFO", 1_000.0, withPartial)
        // Remaining on inst[0] was 1000; should clear it
        assertEquals(0.0, result.updatedInstallments[0].remaining, 0.01)
        // Inst[1] should be untouched
        assertEquals(2_000.0, result.updatedInstallments[1].remaining, 0.01)
    }

    @Test
    fun `payment skips already-paid installments`() {
        val base = LoanCalculator.generateInstallments(
            loanId               = "SKIP-1",
            principal            = 4_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 2,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        // Fully pay first installment
        val withFirst = base.mapIndexed { i, inst ->
            if (i == 0) inst.copy(amountPaid = 2_000.0) else inst
        }
        val result = LoanCalculator.applyPayment("P-SKIP", 2_000.0, withFirst)
        // Should go entirely to installment[1]
        assertEquals(0.0, result.updatedInstallments[1].remaining, 0.01)
        assertEquals(1, result.allocations.size)
        assertEquals(base[1].id, result.allocations[0].installmentId)
    }

    @Test
    fun `no allocations for zero-balance loan`() {
        val base = LoanCalculator.generateInstallments(
            loanId               = "ZERO-1",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val fullyPaid = base.map { it.copy(amountPaid = it.amount) }
        val result = LoanCalculator.applyPayment("P-ZERO", 100.0, fullyPaid)
        assertTrue(result.allocations.isEmpty())
        assertEquals(100.0, result.overpayment, 0.01)
    }

    // ── Installment status transitions ────────────────────────────────────────

    @Test
    fun `installment transitions from Upcoming to Paid after full payment`() {
        val base = LoanCalculator.generateInstallments(
            loanId               = "TR-1",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = future,
        )
        assertEquals(InstallmentStatus.UPCOMING, base[0].statusOn(today))
        val result = LoanCalculator.applyPayment("P-TR1", 2_000.0, base)
        assertEquals(InstallmentStatus.PAID, result.updatedInstallments[0].statusOn(today))
    }

    @Test
    fun `partial payment creates Partial status`() {
        val base = LoanCalculator.generateInstallments(
            loanId               = "TR-2",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = future,
        )
        val result = LoanCalculator.applyPayment("P-TR2", 500.0, base)
        assertEquals(InstallmentStatus.PARTIAL, result.updatedInstallments[0].statusOn(today))
    }

    @Test
    fun `overdue installment with partial payment stays Partial not Overdue`() {
        val base = LoanCalculator.generateInstallments(
            loanId               = "TR-3",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today.minusDays(5),
        )
        val result = LoanCalculator.applyPayment("P-TR3", 500.0, base)
        // Has partial payment — should be PARTIAL not OVERDUE
        assertEquals(InstallmentStatus.PARTIAL, result.updatedInstallments[0].statusOn(today))
    }

    // ── Rounding consistency ──────────────────────────────────────────────────

    @Test
    fun `installment amounts sum to total payable regardless of rounding`() {
        // Use a principal and interest that causes fractional amounts
        val installments = LoanCalculator.generateInstallments(
            loanId               = "ROUND-1",
            principal            = 10_000.0,
            interestAmount       = 2_000.0,
            numberOfInstallments = 6,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val sum = installments.sumOf { it.amount }
        assertEquals(12_000.0, sum, 0.01)
    }

    @Test
    fun `sequential payments reduce outstanding balance correctly`() {
        var installments = LoanCalculator.generateInstallments(
            loanId               = "SEQ-1",
            principal            = 6_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 3,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        // Pay each installment one at a time
        repeat(3) { i ->
            val result = LoanCalculator.applyPayment("P-SEQ-$i", 2_000.0, installments)
            installments = result.updatedInstallments
        }
        val remaining = installments.sumOf { it.remaining }
        assertEquals(0.0, remaining, 0.01)
        assertTrue(installments.all { it.statusOn(today) == InstallmentStatus.PAID })
    }

    // ── Outstanding balance calculation ───────────────────────────────────────

    @Test
    fun `outstanding balance equals totalPayable for new loan with no payments`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "BAL-1",
            principal            = 10_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 5,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val outstanding = installments.sumOf { it.remaining }
        assertEquals(10_000.0, outstanding, 0.01)
    }

    @Test
    fun `outstanding balance decreases after partial payment`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "BAL-2",
            principal            = 10_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 5,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val result = LoanCalculator.applyPayment("P-BAL2", 2_000.0, installments)
        val outstanding = result.updatedInstallments.sumOf { it.remaining }
        assertEquals(8_000.0, outstanding, 0.01)
    }
}
