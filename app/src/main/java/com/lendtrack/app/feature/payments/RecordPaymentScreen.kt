package com.lendtrack.app.feature.payments

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.data.repository.AllocationLineItem
import com.lendtrack.app.data.repository.PaymentPreview
import com.lendtrack.app.domain.model.PaymentMethod
import com.lendtrack.app.feature.borrowers.LtFormField
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.formatShort
import com.lendtrack.app.feature.dashboard.peso
import com.lendtrack.app.feature.loans.LtDropdownField

@Composable
fun RecordPaymentScreen(
    onNavigateBack:   () -> Unit,
    onPaymentRecorded: (paymentId: String) -> Unit,
    viewModel:         RecordPaymentViewModel = hiltViewModel(),
) {
    val fields      by viewModel.fields.collectAsStateWithLifecycle()
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val loanOptions by viewModel.loanOptions.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is RecordPaymentUiState.Success) {
            onPaymentRecorded((uiState as RecordPaymentUiState.Success).paymentId)
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

        LtPanel(title = "Record Payment") {
            // ── Loan selector ──────────────────────────────────────────────
            LtDropdownField(
                label      = "Borrower / Loan *",
                selected   = loanOptions.find { it.loanId == fields.selectedLoanId }?.label
                             ?: "Select a loan…",
                options    = loanOptions.map { it.label to it.loanId },
                onSelected = { _, id -> viewModel.updateField { copy(selectedLoanId = id) } },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LtFormField(
                    label    = "Amount (₱) *",
                    value    = fields.amountText,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(amountText = it) } },
                )
                LtDatePickerField(
                    label          = "Payment Date *",
                    value          = fields.paymentDate,
                    onValueChange  = { viewModel.updateField { copy(paymentDate = it) } },
                    modifier       = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Payment method ─────────────────────────────────────────────
            LtDropdownField(
                label      = "Payment Method",
                selected   = fields.paymentMethod.label,
                options    = PaymentMethod.values().map { it.label to it.name },
                onSelected = { _, name ->
                    viewModel.updateField {
                        copy(paymentMethod = PaymentMethod.valueOf(name))
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LtFormField(
                    label    = "Reference Number",
                    value    = fields.referenceNumber,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(referenceNumber = it) } },
                )
                LtFormField(
                    label    = "Notes",
                    value    = fields.notes,
                    modifier = Modifier.weight(1f),
                    onChange = { viewModel.updateField { copy(notes = it) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            LtNote("Payment is applied to the earliest unpaid installments first (FIFO). Partial payments leave an installment open.")

            // ── Error ──────────────────────────────────────────────────────
            if (uiState is RecordPaymentUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (uiState as RecordPaymentUiState.Error).message,
                    color = LtRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))

            LtButton(
                text     = "Preview Allocation",
                onClick  = { viewModel.previewPayment() },
                icon     = Icons.Filled.Visibility,
                enabled  = uiState !is RecordPaymentUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Allocation preview ─────────────────────────────────────────────
        if (uiState is RecordPaymentUiState.Preview) {
            PaymentAllocationPreviewPanel(
                preview   = (uiState as RecordPaymentUiState.Preview).preview,
                onConfirm = { viewModel.confirmPayment() },
                onEdit    = { viewModel.resetState() },
            )
        }

        if (uiState is RecordPaymentUiState.Loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LtRose)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Allocation preview — shows how the payment will be applied
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaymentAllocationPreviewPanel(
    preview:   PaymentPreview,
    onConfirm: () -> Unit,
    onEdit:    () -> Unit,
) {
    LtPanel(title = "Payment Allocation — Review Before Confirming") {
        // Summary row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AllocationStat("Payment",      preview.paymentAmount.peso())
            AllocationStat("Total Remaining", preview.totalRemaining.peso())
            AllocationStat("Balance After",   preview.balanceAfter.peso(),
                color = if (preview.balanceAfter <= 0) LtSage else LtBurgundy)
            if (preview.overpayment > 0) {
                AllocationStat("Overpayment",  preview.overpayment.peso(), color = LtAmber)
            }
        }

        if (preview.overpayment > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LtAmber.copy(alpha = 0.12f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = LtAmber, modifier = Modifier.size(16.dp))
                Text(
                    "₱${"%.2f".format(preview.overpayment)} exceeds the outstanding balance. Reduce the amount before confirming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Allocation Detail", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        // Show penalties first if any
        if (preview.penaltyItems.isNotEmpty()) {
            Text("Late Penalties", style = MaterialTheme.typography.titleMedium, color = LtBurgundy)
            Spacer(Modifier.height(4.dp))
            AllocationTable(preview.penaltyItems, showInstallmentNumber = false)
            Spacer(Modifier.height(12.dp))
        }

        // Show monthly charges if any
        if (preview.chargeItems.isNotEmpty()) {
            Text("Monthly Interest Charges", style = MaterialTheme.typography.titleMedium, color = LtBurgundy)
            Spacer(Modifier.height(4.dp))
            AllocationTable(preview.chargeItems, showInstallmentNumber = false)
            Spacer(Modifier.height(12.dp))
        }

        // Show installments if any
        if (preview.installmentItems.isNotEmpty()) {
            Text("Installments", style = MaterialTheme.typography.titleMedium, color = LtBurgundy)
            Spacer(Modifier.height(4.dp))
            AllocationTable(preview.installmentItems, showInstallmentNumber = true)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LtButton(
                text     = "← Edit",
                onClick  = onEdit,
                ghost    = true,
                modifier = Modifier.weight(1f),
            )
            LtButton(
                text     = "Confirm Payment",
                onClick  = onConfirm,
                enabled  = preview.overpayment <= 0.005,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AllocationTable(items: List<AllocationLineItem>, showInstallmentNumber: Boolean) {
    // Allocation table header
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (showInstallmentNumber) {
            Text("Installment", style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = LtBurgundy, modifier = Modifier.weight(0.5f))
        }
        Text("Type/Date",   style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
        Text("Applied",     style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
        Text("Remaining",   style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
    }
    LtDivider()

    items.forEach { item ->
        AllocationRow(item, showInstallmentNumber)
        LtDivider()
    }
}

@Composable
private fun AllocationRow(item: AllocationLineItem, showInstallmentNumber: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showInstallmentNumber) {
            Text("#${item.installmentNumber}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        }
        Text(
            if (showInstallmentNumber) item.dueDate.formatShort() else item.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(item.allocatedAmount.peso(),  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtSage), modifier = Modifier.weight(1f))
        Text(item.remainingAfter.peso(),   style = MaterialTheme.typography.bodySmall.copy(color = if (item.remainingAfter <= 0) LtSage else LtBurgundy), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AllocationStat(label: String, value: String, color: androidx.compose.ui.graphics.Color = LtBurgundy) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = color))
    }
}
