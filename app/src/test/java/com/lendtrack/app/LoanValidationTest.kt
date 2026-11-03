package com.lendtrack.app

import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.PaymentFrequency
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for loan validation rules and edge cases.
 */
class LoanValidationTest {

    private val today = LocalDate.of(2025, 10, 12)

    // ── Installment numbering ─────────────────────────────────────────────────

    @Test
    fun `installment numbers are sequential starting from 1`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "NUM-1",
            principal            = 5_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 5,
            paymentFrequency     = PaymentFrequency.WEEKLY,
            firstDueDate         = today,
        )
        installments.forEachIndexed { i, inst ->
            assertEquals(i + 1, inst.installmentNumber)
        }
    }

    @Test
    fun `all installment IDs are unique`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "UNIQ-1",
            principal            = 5_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 8,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        val ids = installments.map { it.id }.toSet()
        assertEquals(installments.size, ids.size)
    }

    // ── Due date calculations ─────────────────────────────────────────────────

    @Test
    fun `monthly installments are spaced 30 days apart`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "DATE-1",
            principal            = 6_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 4,
            paymentFrequency     = PaymentFrequency.MONTHLY,
            firstDueDate         = today,
        )
        for (i in 1 until installments.size) {
            val gap = java.time.temporal.ChronoUnit.DAYS.between(
                installments[i - 1].dueDate,
                installments[i].dueDate,
            )
            assertEquals(30L, gap)
        }
    }

    @Test
    fun `custom frequency uses correct day spacing`() {
        val customDays = 21
        val installments = LoanCalculator.generateInstallments(
            loanId               = "CUST-1",
            principal            = 3_000.0,
            interestAmount       = 0.0,
            numberOfInstallments = 3,
            paymentFrequency     = PaymentFrequency.CUSTOM,
            customFrequencyDays  = customDays,
            firstDueDate         = today,
        )
        val gap = java.time.temporal.ChronoUnit.DAYS.between(
            installments[0].dueDate,
            installments[1].dueDate,
        )
        assertEquals(customDays.toLong(), gap)
    }

    // ── Rounding correctness ──────────────────────────────────────────────────

    @Test
    fun `installments have consistent amounts except last absorbs rounding`() {
        val installments = LoanCalculator.generateInstallments(
            loanId               = "RB-AMT",
            principal            = 12_000.0,
            interestAmount       = 2_880.0,
            numberOfInstallments = 6,
            paymentFrequency     = PaymentFrequency.MONTHLY,
            firstDueDate         = today,
        )
        val firstAmount = installments.first().amount
        // All but last should be the same (last absorbs rounding)
        installments.dropLast(1).forEach { inst ->
            assertEquals(firstAmount, inst.amount, 0.01)
        }
    }

    // ── Totals consistency ────────────────────────────────────────────────────

    @Test
    fun `sum of installments matches principal plus interest`() {
        val principal = 15_000.0
        val interestAmount = 2_700.0
        val n         = 10

        val installments = LoanCalculator.generateInstallments(
            loanId               = "CONS-1",
            principal            = principal,
            interestAmount       = interestAmount,
            numberOfInstallments = n,
            paymentFrequency     = PaymentFrequency.BIWEEKLY,
            firstDueDate         = today,
        )
        assertEquals(principal + interestAmount, installments.sumOf { it.amount }, 0.01)
    }
}
