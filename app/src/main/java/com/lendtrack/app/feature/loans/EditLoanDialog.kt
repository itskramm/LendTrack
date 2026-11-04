package com.lendtrack.app.feature.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.*
import com.lendtrack.app.feature.borrowers.LtFormField
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.formatShort
import com.lendtrack.app.feature.dashboard.peso
import java.time.LocalDate

@Composable
fun EditLoanDialog(
    initialFields: LoanFormFields,
    existingPaymentCount: Int,
    totalPaid: Double,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (LoanFormFields) -> Unit,
) {
    var fields by remember { mutableStateOf(initialFields) }
    var showPreview by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<LoanPreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.9f),
        title = {
            Text("Edit Loan", style = MaterialTheme.typography.headlineMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Payment warning banner ─────────────────────────────────
                if (existingPaymentCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LtAmber.copy(alpha = 0.15f))
                            .border(1.dp, LtAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = LtAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "This loan has $existingPaymentCount payment(s) totaling ${totalPaid.peso()}. " +
                            "Editing will regenerate the installment schedule and re-apply payments. " +
                            "Review the preview carefully.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                // ── Form fields ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LtFormField(
                        label = "Principal Amount (₱) *",
                        value = fields.principalText,
                        modifier = Modifier.weight(1f),
                        onChange = {
                            fields = fields.copy(principalText = it)
                            showPreview = false
                        },
                    )
                    LtFormField(
                        label = "Interest Amount (₱) *",
                        value = fields.interestAmountText,
                        modifier = Modifier.weight(1f),
                        onChange = {
                            fields = fields.copy(interestAmountText = it)
                            showPreview = false
                        },
                    )
                }

                // Show effective rate
                if (fields.principalText.isNotBlank() && fields.interestAmountText.isNotBlank()) {
                    val principal = fields.principalText.toDoubleOrNull() ?: 0.0
                    val interest = fields.interestAmountText.toDoubleOrNull() ?: 0.0
                    if (principal > 0 && interest > 0) {
                        val effectiveRate = (interest / principal) * 100
                        Text(
                            text = "Effective rate: %.1f%%".format(effectiveRate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Payment Frequency *",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LtDropdownField(
                            label = "",
                            selected = fields.paymentFrequency.label,
                            options = PaymentFrequency.values().map { it.label to it.name },
                            onSelected = { _, name ->
                                fields = fields.copy(
                                    paymentFrequency = PaymentFrequency.valueOf(name)
                                )
                                showPreview = false
                            },
                        )
                    }
                    LtFormField(
                        label = "Number of Installments *",
                        value = fields.numberOfInstallments,
                        modifier = Modifier.weight(1f),
                        onChange = {
                            fields = fields.copy(numberOfInstallments = it)
                            showPreview = false
                        },
                    )
                    if (fields.paymentFrequency == PaymentFrequency.CUSTOM) {
                        LtFormField(
                            label = "Days Between Payments *",
                            value = fields.customFrequencyDays,
                            modifier = Modifier.weight(0.8f),
                            onChange = {
                                fields = fields.copy(customFrequencyDays = it)
                                showPreview = false
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LtDatePickerField(
                        label = "Loan Date *",
                        value = fields.loanDateText,
                        onValueChange = {
                            fields = fields.copy(loanDateText = it)
                            showPreview = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    LtDatePickerField(
                        label = "First Payment Due Date *",
                        value = fields.firstDueDateText,
                        onValueChange = {
                            fields = fields.copy(firstDueDateText = it)
                            showPreview = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                LtFormField(
                    label = "Notes",
                    value = fields.notes,
                    onChange = {
                        fields = fields.copy(notes = it)
                        showPreview = false
                    },
                )

                // ── Errors ─────────────────────────────────────────────────
                val currentError = errorMessage
                if (currentError != null) {
                    Text(
                        currentError,
                        color = LtRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val currentPreviewError = previewError
                if (currentPreviewError != null) {
                    Text(
                        currentPreviewError,
                        color = LtRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // ── Calculate preview button ───────────────────────────────
                LtButton(
                    text = "Calculate Preview",
                    onClick = {
                        isCalculating = true
                        previewError = null
                        try {
                            val principal = fields.principalText.toDoubleOrNull()
                                ?: throw IllegalArgumentException("Principal must be a valid number.")
                            val interestAmount = fields.interestAmountText.toDoubleOrNull()
                                ?: throw IllegalArgumentException("Interest amount must be a valid number.")
                            val n = fields.numberOfInstallments.toIntOrNull()
                                ?: throw IllegalArgumentException("Number of installments must be a whole number.")
                            val firstDue = runCatching { LocalDate.parse(fields.firstDueDateText) }.getOrElse {
                                throw IllegalArgumentException("First due date is not a valid date (YYYY-MM-DD).")
                            }
                            val customDays = if (fields.paymentFrequency == PaymentFrequency.CUSTOM)
                                fields.customFrequencyDays.toIntOrNull() ?: 0 else 0

                            val totalPayable = principal + interestAmount
                            val installmentAmount = totalPayable / n

                            val allInstallments = LoanCalculator.generateInstallments(
                                loanId = "preview",
                                principal = principal,
                                interestAmount = interestAmount,
                                numberOfInstallments = n,
                                paymentFrequency = fields.paymentFrequency,
                                customFrequencyDays = customDays,
                                firstDueDate = firstDue,
                            )

                            val expectedEndDate = allInstallments.last().dueDate

                            val totals = LoanTotals(
                                principal = principal,
                                interestAmount = interestAmount,
                                totalPayable = totalPayable,
                                installmentAmount = installmentAmount,
                            )

                            preview = LoanPreview(
                                totals = totals,
                                sampleInstallments = allInstallments,
                                expectedEndDate = expectedEndDate,
                            )
                            showPreview = true
                        } catch (e: Exception) {
                            previewError = e.message ?: "Validation error."
                            showPreview = false
                        } finally {
                            isCalculating = false
                        }
                    },
                    icon = Icons.Filled.Calculate,
                    enabled = !isCalculating,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Preview panel ──────────────────────────────────────────
                val currentPreview = preview
                if (showPreview && currentPreview != null) {
                    Spacer(Modifier.height(8.dp))
                    EditLoanPreviewPanel(preview = currentPreview)
                }
            }
        },
        confirmButton = {
            LtButton(
                text = "Confirm",
                onClick = { onSave(fields) },
            )
        },
        dismissButton = {
            LtButton(
                text = "Cancel",
                onClick = onDismiss,
                ghost = true,
            )
        },
    )
}

@Composable
private fun EditLoanPreviewPanel(preview: LoanPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Repayment Preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Totals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PreviewStatSmall("Principal", preview.totals.principal.peso())
            PreviewStatSmall("Interest", preview.totals.interestAmount.peso())
            PreviewStatSmall("Total Payable", preview.totals.totalPayable.peso())
            PreviewStatSmall("Per Installment", preview.totals.installmentAmount.peso())
        }

        Spacer(Modifier.height(4.dp))

        // Schedule table header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "#",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
            Text(
                "Due Date",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Amount",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        LtDivider()

        // Installment rows (show all)
        preview.sampleInstallments.forEach { inst ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    inst.installmentNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    inst.dueDate.formatShort(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    inst.amount.peso(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
            }
            LtDivider()
        }

        Text(
            "Last payment due: ${preview.expectedEndDate.formatFull()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewStatSmall(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        )
    }
}
