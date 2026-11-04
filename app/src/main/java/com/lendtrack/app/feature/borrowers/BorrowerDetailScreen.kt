package com.lendtrack.app.feature.borrowers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.formatShort
import com.lendtrack.app.feature.dashboard.peso
import com.lendtrack.app.feature.loans.EditLoanDialog
import java.time.LocalDate

@Composable
fun BorrowerDetailScreen(
    onNavigateBack:  () -> Unit,
    onRecordPayment: (loanId: String) -> Unit,
    onAddLoan:       (borrowerId: String) -> Unit,
    viewModel:       BorrowerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    // Edit borrower dialog
    if (viewModel.showEditDialog) {
        val initial = remember(viewModel.showEditDialog) { viewModel.currentBorrowerAsFields() }
        AddBorrowerDialog(
            initialFields = initial,          // G-9: now actually passed to the dialog
            errorMessage  = viewModel.editError,
            onDismiss     = { viewModel.dismissEdit() },
            onSave        = { fields -> viewModel.saveEdit(fields) },
        )
    }

    // Edit loan dialog
    if (viewModel.showEditLoanDialog) {
        val loanId = viewModel.editLoanId
        val loan = state.loans.find { it.loan.id == loanId }
        val payments = state.payments.filter { it.loanId == loanId && !it.isReversed }
        val existingPaymentCount = payments.size
        val totalPaid = payments.sumOf { it.amount }

        EditLoanDialog(
            initialFields = remember(viewModel.showEditLoanDialog) {
                viewModel.currentLoanAsFields(loanId)
            },
            existingPaymentCount = existingPaymentCount,
            totalPaid = totalPaid,
            errorMessage = viewModel.editLoanError,
            onDismiss = { viewModel.dismissLoanEdit() },
            onSave = { fields -> viewModel.saveLoanEdit(fields) },
        )
    }

    // Delete borrower confirmation dialog
    if (viewModel.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            containerColor   = MaterialTheme.colorScheme.surface,
            title            = { Text("Delete Borrower?", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column {
                    Text("This action is permanent and cannot be undone.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = viewModel.deleteReason,
                        onValueChange = { viewModel.updateDeleteReason(it) },
                        label         = { Text("Reason (required)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = false,
                        maxLines      = 3,
                    )
                    if (viewModel.deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(viewModel.deleteError!!, color = LtRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                LtButton(
                    text    = "Delete",
                    onClick = { viewModel.deleteBorrower(onSuccess = onNavigateBack) },
                )
            },
            dismissButton = {
                LtButton(text = "Cancel", onClick = { viewModel.dismissDelete() }, ghost = true)
            },
        )
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LtRose)
        }
        return
    }

    val borrower = state.borrower ?: run {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            LtNote("Borrower not found.")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Header row: back + edit + delete ───────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LtButton(text = "← Back", onClick = onNavigateBack, ghost = true, small = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LtButton(text = "✎ Edit", onClick = { viewModel.openEdit() }, ghost = true, small = true)
                val activeLoansCount = state.loans.count { it.loan.status == LoanStatus.ACTIVE }
                if (activeLoansCount == 0) {
                    LtButton(text = "🗑 Delete", onClick = { viewModel.openDelete() }, ghost = true, small = true)
                }
            }
        }

        // ── Borrower header card ───────────────────────────────────────────
        LtPanel(
            title  = borrower.name,
            action = {
                LtButton(
                    text    = "+ Add Loan",
                    onClick = { onAddLoan(borrower.id) },
                    icon    = Icons.Filled.AddCard,
                    small   = true,
                )
            },
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ContactRow(Icons.Filled.Phone, borrower.contactNumber)
                    if (borrower.preferredPaymentChannel.label.isNotBlank()) {
                        ContactRow(Icons.Filled.Payment, "Prefers ${borrower.preferredPaymentChannel.label}")
                    }
                    if (borrower.address.isNotBlank())   ContactRow(Icons.Filled.Home,     borrower.address)
                    if (borrower.barangay.isNotBlank())  ContactRow(Icons.Filled.LocationOn, borrower.barangay)
                    if (borrower.occupation.isNotBlank()) ContactRow(Icons.Filled.Work,    borrower.occupation)
                    if (borrower.secondaryContactName.isNotBlank()) {
                        ContactRow(Icons.Filled.Person, "${borrower.secondaryContactName} (${borrower.secondaryContactRelationship}) · ${borrower.secondaryContactNumber}")
                    }
                    if (borrower.governmentIdType.isNotBlank()) {
                        ContactRow(Icons.Filled.Badge, "${borrower.governmentIdType}: ${borrower.governmentIdNumber}")
                    }
                    if (borrower.notes.isNotBlank()) ContactRow(Icons.Filled.Notes, borrower.notes)
                }

                // Outstanding balance highlight
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier            = Modifier
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, LtDusty, CardShape)
                        .padding(16.dp),
                ) {
                    Text("Outstanding",  style = MaterialTheme.typography.labelMedium, color = LtMuted)
                    Text(
                        state.totalOutstanding.peso(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.totalOutstanding > 0) LtBurgundy else LtSage,
                    )
                    Spacer(Modifier.height(4.dp))
                    val activeCount = state.loans.count { it.loan.status == LoanStatus.ACTIVE }
                    Text(
                        "$activeCount active loan${if (activeCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LtMuted,
                    )
                }
            }

            if (borrower.syncStatus != SyncStatus.SYNCED) {
                Spacer(Modifier.height(8.dp))
                LtSyncBadge(borrower.syncStatus)
            }
        }

        // ── Loans ──────────────────────────────────────────────────────────
        if (state.loans.isEmpty()) {
            LtPanel(title = "Loans") {
                LtEmptyState("No loans yet. Tap \"+ Add Loan\" to create the first loan.")
            }
        } else {
            state.loans.forEach { lwb ->
                LoanDetailCard(
                    lwb             = lwb,
                    today           = today,
                    onRecordPayment = { onRecordPayment(lwb.loan.id) },
                    onEdit          = { viewModel.openLoanEdit(lwb.loan.id) },
                )
            }
        }

        // ── Payment history ────────────────────────────────────────────────
        LtPanel(title = "Payment History") {
            if (state.payments.isEmpty()) {
                LtEmptyState("No payments recorded yet.")
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Date",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                    Text("Amount", style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                    Text("Method", style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                    Text("Status", style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(0.8f))
                }
                LtDivider()
                state.payments.forEach { payment ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(payment.paymentDate.formatFull(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(payment.amount.peso(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtSage), modifier = Modifier.weight(1f))
                        Text(payment.paymentMethod.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Row(modifier = Modifier.weight(0.8f)) {
                            if (payment.isReversed) {
                                Box(
                                    modifier = Modifier
                                        .clip(BadgeShape)
                                        .background(LtRed.copy(alpha = 0.15f))
                                        .border(1.dp, LtRed, BadgeShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) { Text("Reversed", style = MaterialTheme.typography.labelSmall, color = LtRed) }
                            } else {
                                LtSyncBadge(payment.syncStatus)
                            }
                        }
                    }
                    LtDivider()
                }
            }
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Loan detail card ──────────────────────────────────────────────────────────

@Composable
private fun LoanDetailCard(
    lwb:             LoanWithBalance,
    today:           LocalDate,
    onRecordPayment: () -> Unit,
    onEdit:          () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val loan     = lwb.loan

    LtPanel(
        title  = "Loan — ${loan.loanDate.formatFull()}",
        action = {
            when (loan.status) {
                LoanStatus.ACTIVE    -> LtStatusBadge(InstallmentStatus.UPCOMING)
                LoanStatus.COMPLETED -> LtStatusBadge(InstallmentStatus.PAID)
                LoanStatus.CANCELLED -> Box(
                    modifier = Modifier
                        .clip(BadgeShape)
                        .background(LtMuted)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) { Text("Cancelled", style = MaterialTheme.typography.labelSmall, color = Color.White) }
            }
        },
    ) {
        // Financial grid
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoanFinancialItem("Principal",    loan.principal.peso(),      Modifier.weight(1f))
            LoanFinancialItem("Interest",     loan.interestAmount.peso(), Modifier.weight(1f))
            LoanFinancialItem("Total Payable",loan.totalPayable.peso(),   Modifier.weight(1f))
            LoanFinancialItem(
                label      = "Balance",
                value      = lwb.remainingBalance.peso(),
                modifier   = Modifier.weight(1f),
                valueColor = if (lwb.remainingBalance > 0) LtBurgundy else LtSage,
            )
        }

        Spacer(Modifier.height(8.dp))
        LtNote("${loan.paymentFrequency.label} · ${loan.numberOfInstallments} installments")
        Spacer(Modifier.height(12.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) "▲ Hide installments" else "▼ Show installments",
                    color = LtRose,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (loan.status == LoanStatus.ACTIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LtButton(text = "✎ Edit", onClick = onEdit, small = true, ghost = true)
                    if (lwb.remainingBalance > 0) {
                        LtButton(text = "₱  Record Payment", onClick = onRecordPayment, small = true)
                    }
                }
            }
        }

        // Expandable installment schedule
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            LtDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("#",         style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                Text("Due",       style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Amount",    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Paid",      style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Remaining", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Status",    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = LtBurgundy, modifier = Modifier.weight(1f))
            }
            LtDivider()
            lwb.installments.forEach { inst ->
                val status = inst.statusOn(today)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (status == InstallmentStatus.OVERDUE) LtRed.copy(alpha = 0.04f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(inst.installmentNumber.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                    Text(inst.dueDate.formatShort(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(inst.amount.peso(),         style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(inst.amountPaid.peso(),     style = MaterialTheme.typography.bodySmall.copy(color = LtSage), modifier = Modifier.weight(1f))
                    Text(inst.remaining.peso(),      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.weight(1f)) { LtStatusBadge(status) }
                }
                LtDivider()
            }
        }
    }
}

@Composable
private fun LoanFinancialItem(
    label:      String,
    value:      String,
    modifier:   Modifier = Modifier,
    valueColor: Color = LtBurgundy,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(color = valueColor, fontWeight = FontWeight.SemiBold))
    }
}
