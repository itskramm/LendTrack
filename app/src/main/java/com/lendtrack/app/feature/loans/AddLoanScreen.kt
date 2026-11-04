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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.*
import com.lendtrack.app.feature.borrowers.LtFormField
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.formatShort
import com.lendtrack.app.feature.dashboard.peso

@Composable
fun AddLoanScreen(
    onNavigateBack:  () -> Unit,
    onLoanCreated:   (loanId: String) -> Unit,
    viewModel:       AddLoanViewModel = hiltViewModel(),
) {
    val fields    by viewModel.fields.collectAsStateWithLifecycle()
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val borrowers by viewModel.borrowers.collectAsStateWithLifecycle()

    // Navigate away on success
    LaunchedEffect(uiState) {
        if (uiState is AddLoanUiState.Success) {
            onLoanCreated((uiState as AddLoanUiState.Success).loanId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LtButton(text = "← Back", onClick = onNavigateBack, ghost = true, small = true)
        }

        LtPanel(title = "New Loan") {
            // ── Borrower selector ──────────────────────────────────────────
            LtDropdownField(
                label      = "Borrower *",
                selected   = borrowers.find { it.id == fields.borrowerId }?.name ?: "Select borrower…",
                options    = borrowers.map { it.name to it.id },
                onSelected = { _, id -> viewModel.updateField { copy(borrowerId = id) } },
            )

            Spacer(Modifier.height(12.dp))

            // ── Two-column financial inputs ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LtFormField(
                    label    = "Principal Amount (₱) *",
                    value    = fields.principalText,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(principalText = it) } },
                )
                LtFormField(
                    label    = "Interest Amount (₱) *",
                    value    = fields.interestAmountText,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(interestAmountText = it) } },
                )
            }
            
            // Show effective rate if both values entered
            if (fields.principalText.isNotBlank() && fields.interestAmountText.isNotBlank()) {
                val principal = fields.principalText.toDoubleOrNull() ?: 0.0
                val interest = fields.interestAmountText.toDoubleOrNull() ?: 0.0
                if (principal > 0 && interest > 0) {
                    val effectiveRate = (interest / principal) * 100
                    Text(
                        text = "Effective rate: %.1f%%".format(effectiveRate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Monthly Interest & Late Penalty ────────────────────────────
            Text(
                text = "Recurring Charges (Optional)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LtFormField(
                        label    = "Monthly Interest (₱)",
                        value    = fields.monthlyInterestText,
                        onChange = { viewModel.updateField { copy(monthlyInterestText = it) } },
                    )
                    Text(
                        text = "Recurring charge every month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    LtFormField(
                        label    = "Late Penalty (₱)",
                        value    = fields.latePenaltyText,
                        onChange = { viewModel.updateField { copy(latePenaltyText = it) } },
                    )
                    Text(
                        text = "Fee for overdue installments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Column(modifier = Modifier.fillMaxWidth(0.5f)) {
                LtFormField(
                    label    = "Penalty Grace Period (days)",
                    value    = fields.graceDaysText,
                    onChange = { viewModel.updateField { copy(graceDaysText = it) } },
                )
                Text(
                    text = "Days before late penalty applies (e.g., 3)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Payment schedule ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Payment Frequency *", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LtDropdownField(
                        label      = "",
                        selected   = fields.paymentFrequency.label,
                        options    = PaymentFrequency.values().map { it.label to it.name },
                        onSelected = { _, name ->
                            viewModel.updateField {
                                copy(paymentFrequency = PaymentFrequency.valueOf(name))
                            }
                        },
                    )
                }
                LtFormField(
                    label    = "Number of Installments *",
                    value    = fields.numberOfInstallments,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(numberOfInstallments = it) } },
                )
                if (fields.paymentFrequency == PaymentFrequency.CUSTOM) {
                    LtFormField(
                        label    = "Days Between Payments *",
                        value    = fields.customFrequencyDays,
                        modifier = Modifier.weight(0.8f),
                        onChange = { viewModel.updateField { copy(customFrequencyDays = it) } },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Dates ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LtDatePickerField(
                    label          = "Loan Date *",
                    value          = fields.loanDateText,
                    onValueChange  = { viewModel.updateField { copy(loanDateText = it) } },
                    modifier       = Modifier.weight(1f),
                )
                LtDatePickerField(
                    label          = "First Payment Due Date *",
                    value          = fields.firstDueDateText,
                    onValueChange  = { viewModel.updateField { copy(firstDueDateText = it) } },
                    modifier       = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            LtFormField(
                label    = "Notes",
                value    = fields.notes,
                onChange = { viewModel.updateField { copy(notes = it) } },
            )

            // ── Validation hint ────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            LtNote(
                "Dates: YYYY-MM-DD format.  " +
                "Annual rate: enter 20 for 20 %. " +
                "Interest method cannot be changed after saving."
            )

            // ── Error ──────────────────────────────────────────────────────
            if (uiState is AddLoanUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (uiState as AddLoanUiState.Error).message,
                    color  = LtRed,
                    style  = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Calculate preview button ───────────────────────────────────
            LtButton(
                text     = "Calculate Repayment Schedule",
                onClick  = { viewModel.calculatePreview() },
                icon     = Icons.Filled.Calculate,
                enabled  = uiState !is AddLoanUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Repayment Preview ──────────────────────────────────────────────
        if (uiState is AddLoanUiState.Preview) {
            RepaymentPreviewPanel(
                preview  = (uiState as AddLoanUiState.Preview).preview,
                fields   = fields,
                onConfirm = { viewModel.confirmAndSave() },
                onEdit    = { viewModel.resetState() },
            )
        }

        if (uiState is AddLoanUiState.Loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Repayment preview panel — shown before user confirms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RepaymentPreviewPanel(
    preview:   LoanPreview,
    fields:    LoanFormFields,
    onConfirm: () -> Unit,
    onEdit:    () -> Unit,
) {
    LtPanel(title = "Repayment Summary — Please Review") {
        // Totals
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PreviewStat("Principal",       preview.totals.principal.peso())
            PreviewStat("Interest",        preview.totals.interestAmount.peso())
            PreviewStat("Total Payable",   preview.totals.totalPayable.peso())
            PreviewStat("Per Installment", preview.totals.installmentAmount.peso())
        }

        Spacer(Modifier.height(12.dp))

        // Simple explanation
        LtNote("Interest is added as a fixed amount to the principal. The total is divided equally across all installments.")

        Spacer(Modifier.height(12.dp))

        // Schedule table
        Text("Installment Schedule", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("#",      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
            Text("Due Date", style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("Amount",   style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        }
        LtDivider()

        // Show all installments (scrollable within panel via parent scroll)
        preview.sampleInstallments.forEach { inst ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(inst.installmentNumber.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                Text(inst.dueDate.formatShort(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(inst.amount.peso(),         style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            }
            LtDivider()
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Last payment due: ${preview.expectedEndDate.formatFull()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Warning box before confirm
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LtAmber.copy(alpha = 0.12f))
                .border(1.dp, LtAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = LtAmber, modifier = Modifier.size(18.dp))
            Text(
                "The interest method and installment schedule cannot be changed after saving. " +
                "Review the figures above before confirming.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LtButton(text = "← Edit Details", onClick = onEdit, ghost = true, modifier = Modifier.weight(1f))
            LtButton(text = "Confirm & Save Loan", onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable dropdown field used in Add Loan and Record Payment forms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtDropdownField(
    label:      String,
    selected:   String,
    options:    List<Pair<String, String>>,   // label to value
    onSelected: (label: String, value: String) -> Unit,
    modifier:   Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(
                onClick  = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = InputShape,
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor   = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    selected,
                    style    = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
                containerColor   = MaterialTheme.colorScheme.surface,
            ) {
                options.forEach { (lbl, value) ->
                    DropdownMenuItem(
                        text    = { Text(lbl, style = MaterialTheme.typography.bodySmall) },
                        onClick = { onSelected(lbl, value); expanded = false },
                    )
                }
            }
        }
    }
}
