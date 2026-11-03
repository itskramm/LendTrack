package com.lendtrack.app

import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.InstallmentStatus
import com.lendtrack.app.domain.model.PaymentFrequency
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LoanCalculatorTest {

    private val today = LocalDate.of(2025, 10, 12)

    // ── Fixed interest amount tests ────────────────────────────────────────────

    @Test
    fun `total payable equals principal plus interest amount`() {
        val principal = 10_000.0
        val interestAmount = 2_400.0
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = principal,
            interestAmount       = interestAmount,
            numberOfInstallments = 6,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val totalPayable = installments.sumOf { it.amount }
        assertEquals(principal + interestAmount, totalPayable, 0.01)
    }

    @Test
    fun `installment sum equals total payable`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 12_000.0,
            interestAmount       = 2_400.0,
            numberOfInstallments = 8,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today.plusDays(14),
        )
        val sum = installments.sumOf { it.amount }
        assertEquals(14_400.0, sum, 0.01)
    }

    @Test
    fun `generates correct number of installments`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 5_000.0,
            interestAmount       = 900.0,
            numberOfInstallments = 10,
            paymentFrequency     = PaymentFrequency.WEEKLY,
            firstDueDate         = today,
        )
        assertEquals(10, installments.size)
    }

    @Test
    fun `due dates spaced by frequency days`() {
        val firstDue = today
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 5_000.0,
            interestAmount       = 600.0,
            numberOfInstallments = 4,
            paymentFrequency     = PaymentFrequency.WEEKLY,
            firstDueDate         = firstDue,
        )
        assertEquals(firstDue,              installments[0].dueDate)
        assertEquals(firstDue.plusDays(7),  installments[1].dueDate)
        assertEquals(firstDue.plusDays(14), installments[2].dueDate)
        assertEquals(firstDue.plusDays(21), installments[3].dueDate)
    }

    // ── Zero interest ──────────────────────────────────────────────────────────

    @Test
    fun `zero interest - total payable equals principal`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 8_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 4,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val totalPayable = installments.sumOf { it.amount }
        assertEquals(8_000.0, totalPayable, 0.01)
    }

    // ── Payment allocation ─────────────────────────────────────────────────────

    @Test
    fun `full payment clears first installment`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L3",
            principal            = 6_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 3,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val result = LoanCalculator.applyPayment("P1", 2_000.0, installments)
        assertEquals(0.0, result.updatedInstallments[0].remaining, 0.01)
        assertTrue(result.updatedInstallments[1].remaining > 0)
        assertEquals(0.0, result.overpayment, 0.01)
        assertEquals(1, result.allocations.size)
    }

    @Test
    fun `partial payment reduces first installment balance`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L4",
            principal            = 4_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 2,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val result = LoanCalculator.applyPayment("P2", 500.0, installments)
        assertEquals(1_500.0, result.updatedInstallments[0].remaining, 0.01)
        assertEquals(2_000.0, result.updatedInstallments[1].remaining, 0.01)
    }

    @Test
    fun `payment spanning multiple installments allocates correctly`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L5",
            principal            = 6_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 3,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        // Pay 2.5 installments worth
        val result = LoanCalculator.applyPayment("P3", 5_000.0, installments)
        assertEquals(0.0,     result.updatedInstallments[0].remaining, 0.01)
        assertEquals(0.0,     result.updatedInstallments[1].remaining, 0.01)
        assertEquals(1_000.0, result.updatedInstallments[2].remaining, 0.01)
        assertEquals(3, result.allocations.size)
    }

    @Test
    fun `overpayment is returned in result`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L6",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val result = LoanCalculator.applyPayment("P4", 2_500.0, installments)
        assertEquals(0.0,   result.updatedInstallments[0].remaining, 0.01)
        assertEquals(500.0, result.overpayment, 0.01)
    }

    // ── Installment status ─────────────────────────────────────────────────────

    @Test
    fun `paid installment has Paid status`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L7",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today.minusDays(7),
        )
        val result  = LoanCalculator.applyPayment("P5", 2_000.0, installments)
        val updated = result.updatedInstallments.first()
        assertEquals(InstallmentStatus.PAID, updated.statusOn(today))
    }

    @Test
    fun `past due date with remaining balance is Overdue`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L8",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today.minusDays(3),
        )
        assertEquals(InstallmentStatus.OVERDUE, installments.first().statusOn(today))
    }

    @Test
    fun `due today is Due Soon`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "L9",
            principal            = 2_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 1,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        assertEquals(InstallmentStatus.DUE_SOON, installments.first().statusOn(today))
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `zero principal throws`() {
        LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 0.0,
            interestAmount       = 100.0,
            numberOfInstallments = 6,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative interest throws`() {
        LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 5_000.0,
            interestAmount       = -100.0,
            numberOfInstallments = 6,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero installments throws`() {
        LoanCalculator.generateInstallments(
            loanId               = "L1",
            principal            = 5_000.0,
            interestAmount       = 500.0,
            numberOfInstallments = 0,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
    }
}
