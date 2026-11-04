package com.lendtrack.app.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.Installment
import com.lendtrack.app.domain.model.LoanSummary
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.peso
import java.time.LocalDate

@Composable
fun BalanceLedgerScreen(
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val loanSummaries by viewModel.loanSummaries.collectAsStateWithLifecycle()
    val payments      by viewModel.recentPayments.collectAsStateWithLifecycle()
    var query         by remember { mutableStateOf("") }

    // Map from loanId → loaded installments (populated on expand)
    val expandedInstallments = remember { mutableStateMapOf<String, List<Installment>>() }
    val expandedLoan         = remember { mutableStateOf<String?>(null) }

    // Delete loan state
    var loanToDelete by remember { mutableStateOf<LoanSummary?>(null) }
    var deleteReason by remember { mutableStateOf("") }
    var deleteError  by remember { mutableStateOf<String?>(null) }
    var deleting     by remember { mutableStateOf(false) }
    var triggerDelete by remember { mutableStateOf(false) }

    // Perform delete when triggered
    LaunchedEffect(triggerDelete) {
        if (triggerDelete && loanToDelete != null) {
            deleting = true
            val result = viewModel.deleteLoan(loanToDelete!!.loan.id, deleteReason)
            deleting = false
            if (result.isSuccess) {
                loanToDelete = null
                deleteReason = ""
                deleteError  = null
            } else {
                deleteError = result.exceptionOrNull()?.message ?: "Delete failed"
            }
            triggerDelete = false
        }
    }

    // Delete loan confirmation dialog
    loanToDelete?.let { ls ->
        AlertDialog(
            onDismissRequest = { if (!deleting) { loanToDelete = null; deleteReason = ""; deleteError = null } },
            containerColor   = MaterialTheme.colorScheme.surface,
            title            = { Text("Delete Loan?", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column {
                    Text("Delete loan for ${ls.borrower.name}? All installments will be removed. This action cannot be undone.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = deleteReason,
                        onValueChange = { deleteReason = it },
                        label         = { Text("Reason (required)") },
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = !deleting,
                        singleLine    = false,
                        maxLines      = 3,
                    )
                    if (deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError!!, color = LtRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                LtButton(
                    text    = if (deleting) "Deleting..." else "Delete",
                    onClick = {
                        if (deleteReason.isBlank()) {
                            deleteError = "Reason is required"
                            return@LtButton
                        }
                        triggerDelete = true
                    },
                    enabled = !deleting,
                )
            },
            dismissButton = {
                LtButton(text = "Cancel", onClick = { loanToDelete = null; deleteReason = ""; deleteError = null }, ghost = true, enabled = !deleting)
            },
        )
    }

    LaunchedEffect(query) { viewModel.onSearch(query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        LtPanel(title = "Balance Ledger") {
            LtSearchField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = "Search by borrower…",
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = Icons.Filled.Search,
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Borrower",  style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(2f))
                Text("Principal", style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Interest",  style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Payable",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Applied",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))  // G-7
                Text("Balance",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
            }
            LtDivider()

            if (loanSummaries.isEmpty()) {
                LtEmptyState("No loans yet.")
            } else {
                loanSummaries.forEach { ls ->
                    LedgerRow(
                        ls           = ls,
                        isExpanded   = expandedLoan.value == ls.loan.id,
                        installments = expandedInstallments[ls.loan.id],
                        onToggle     = {
                            if (expandedLoan.value == ls.loan.id) {
                                expandedLoan.value = null
                            } else {
                                expandedLoan.value = ls.loan.id
                                // Lazy-load installments when expanding
                                if (!expandedInstallments.containsKey(ls.loan.id)) {
                                    // trigger load via side-effect
                                }
                            }
                        },
                        onDelete     = { loanToDelete = ls },
                    )
                }
            }

            // Load installments for expanded row
            LaunchedEffect(expandedLoan.value) {
                val id = expandedLoan.value ?: return@LaunchedEffect
                if (!expandedInstallments.containsKey(id)) {
                    expandedInstallments[id] = viewModel.getInstallmentsForLoan(id)
                }
            }

            // Transaction history
            LtSectionHeader("Transaction History", Modifier.padding(top = 8.dp))

            if (payments.isEmpty()) {
                LtNote("No transactions recorded yet.")
            } else {
                payments.forEach { pwb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("₱", color = LtRose, style = MaterialTheme.typography.bodySmall)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${pwb.borrower.name} paid ${pwb.payment.amount.peso()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${pwb.payment.paymentDate.formatFull()} · ${pwb.payment.paymentMethod.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LtSyncBadge(pwb.payment.syncStatus)
                    }
                    LtDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            LtNote("Tap a row to see its installments. Balance = principal + interest − payments applied.")
        }
    }
}

@Composable
private fun LedgerRow(
    ls:           LoanSummary,
    isExpanded:   Boolean,
    installments: List<Installment>?,
    onToggle:     () -> Unit,
    onDelete:     () -> Unit,
) {
    val today = LocalDate.now()
    // G-6: use totalApplied for the pre-expand balance so it's always correct.
    // When installments are loaded (post-expand) use the precise remaining sum.
    val displayBalance = installments?.sumOf { it.remaining }
        ?: (ls.loan.totalPayable - ls.totalApplied)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ls.borrower.name,
            style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtBurgundy),
            modifier = Modifier.weight(2f),
        )
        Text(ls.loan.principal.peso(),      style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(ls.loan.interestAmount.peso(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(ls.loan.totalPayable.peso(),   style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        // G-7: Applied column
        Text(
            ls.totalApplied.peso(),
            style    = MaterialTheme.typography.bodySmall.copy(color = LtSage),
            modifier = Modifier.weight(1f),
        )
        Text(
            displayBalance.peso(),
            style    = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color      = if (displayBalance > 0) LtBurgundy else LtSage,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onDelete() },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete loan", tint = LtRed, modifier = Modifier.size(18.dp))
        }
    }

    // Expandable installment detail
    if (isExpanded) {
        if (installments == null) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LtRose, modifier = Modifier.size(20.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                LtNote("Installment breakdown:")
                Spacer(Modifier.height(4.dp))
                installments.forEach { inst ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#${inst.installmentNumber} · ${inst.dueDate.formatFull()} · due ${inst.amount.peso()} · paid ${inst.amountPaid.peso()} · left ${inst.remaining.peso()}",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        LtStatusBadge(inst.statusOn(today))
                    }
                    LtDivider()
                }
            }
        }
    }

    LtDivider()
}
