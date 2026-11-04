package com.lendtrack.app.feature.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.Payment
import com.lendtrack.app.domain.model.SyncStatus
import com.lendtrack.app.feature.dashboard.PaymentWithBorrower
import com.lendtrack.app.feature.dashboard.formatFull
import com.lendtrack.app.feature.dashboard.peso

@Composable
fun PaymentsScreen(
    onRecordPayment: () -> Unit = {},
    viewModel:       PaymentsViewModel = hiltViewModel(),
) {
    val payments   by viewModel.payments.collectAsStateWithLifecycle()
    var query      by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf<PaymentWithBorrower?>(null) }

    // Delete payment state
    var paymentToDelete by remember { mutableStateOf<PaymentWithBorrower?>(null) }
    var deleteReason by remember { mutableStateOf("") }
    var deleteError  by remember { mutableStateOf<String?>(null) }
    var deleting     by remember { mutableStateOf(false) }
    var triggerDelete by remember { mutableStateOf(false) }

    // Perform delete when triggered
    LaunchedEffect(triggerDelete) {
        if (triggerDelete && paymentToDelete != null) {
            deleting = true
            val result = viewModel.deletePayment(paymentToDelete!!.payment.id, deleteReason)
            deleting = false
            if (result.isSuccess) {
                paymentToDelete = null
                deleteReason = ""
                deleteError  = null
            } else {
                deleteError = result.exceptionOrNull()?.message ?: "Delete failed"
            }
            triggerDelete = false
        }
    }

    // Delete payment confirmation dialog
    paymentToDelete?.let { pwb ->
        AlertDialog(
            onDismissRequest = { if (!deleting) { paymentToDelete = null; deleteReason = ""; deleteError = null } },
            containerColor   = MaterialTheme.colorScheme.surface,
            title            = { Text("Delete Payment?", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column {
                    Text("Delete payment of ${pwb.payment.amount.peso()} from ${pwb.borrower.name}? This will restore the loan balance. This action cannot be undone.")
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
                LtButton(text = "Cancel", onClick = { paymentToDelete = null; deleteReason = ""; deleteError = null }, ghost = true, enabled = !deleting)
            },
        )
    }

    // Payment detail bottom sheet
    selectedPayment?.let { pwb ->
        PaymentDetailSheet(
            pwb      = pwb,
            onDismiss = { selectedPayment = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        LtPanel(
            title = "Payments",
            action = {
                LtButton(
                    text    = "₱  Record Payment",
                    onClick = onRecordPayment,
                    icon    = Icons.Filled.AddCard,
                    small   = true,
                )
            },
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LtSearchField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = "Filter by borrower…",
                    modifier      = Modifier.weight(1f),
                    leadingIcon   = Icons.Filled.Search,
                )
                // G-5: replaced plain text field with date picker
                LtDatePickerField(
                    label         = "",
                    value         = dateFilter,
                    onValueChange = { dateFilter = it },
                    modifier      = Modifier.weight(0.7f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Date",     style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Borrower", style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1.5f))
                Text("Method",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Amount",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
                Text("Sync",     style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(0.8f))
            }
            LtDivider()

            // Filtering is done entirely in the composable — no ViewModel query needed
            val filtered = payments.filter { pwb ->
                (query.isBlank()      || pwb.borrower.name.contains(query, ignoreCase = true)) &&
                (dateFilter.isBlank() || pwb.payment.paymentDate.toString() == dateFilter)
            }

            if (filtered.isEmpty()) {
                LtEmptyState(
                    if (payments.isEmpty()) "No payments recorded yet."
                    else "No payments match this filter.",
                )
            } else {
                filtered.forEach { pwb ->
                    // G-3: row tap opens payment detail sheet
                    PaymentTableRow(
                        pwb      = pwb,
                        onClick  = { selectedPayment = pwb },
                        onDelete = { paymentToDelete = pwb },
                    )
                    LtDivider()
                }
            }
        }
    }
}

@Composable
private fun PaymentTableRow(pwb: PaymentWithBorrower, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pwb.payment.paymentDate.formatFull(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            pwb.borrower.name,
            style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtBurgundy),
            modifier = Modifier.weight(1.5f),
        )
        Text(pwb.payment.paymentMethod.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            pwb.payment.amount.peso(),
            style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtSage),
            modifier = Modifier.weight(1f),
        )
        LtSyncBadge(pwb.payment.syncStatus, modifier = Modifier.weight(0.8f))
        IconButton(
            onClick = { onDelete() },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete payment", tint = LtRed, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// G-3: Payment detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentDetailSheet(pwb: PaymentWithBorrower, onDismiss: () -> Unit) {
    val p = pwb.payment
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Payment Details", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Borrower",   pwb.borrower.name)
                HorizontalDivider(color = LtDivider)
                DetailRow("Amount",     p.amount.peso(),              valueColor = LtSage)
                HorizontalDivider(color = LtDivider)
                DetailRow("Date",       p.paymentDate.formatFull())
                HorizontalDivider(color = LtDivider)
                DetailRow("Method",     p.paymentMethod.label)
                if (p.referenceNumber.isNotBlank()) {
                    HorizontalDivider(color = LtDivider)
                    DetailRow("Reference", p.referenceNumber)
                }
                if (p.notes.isNotBlank()) {
                    HorizontalDivider(color = LtDivider)
                    DetailRow("Notes", p.notes)
                }
                HorizontalDivider(color = LtDivider)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Sync status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LtSyncBadge(p.syncStatus)
                }
                if (p.isReversed) {
                    HorizontalDivider(color = LtDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LtRed.copy(alpha = 0.08f))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("Payment Reversed", style = MaterialTheme.typography.labelLarge.copy(color = LtRed, fontWeight = FontWeight.SemiBold))
                            if (p.reversalReason.isNotBlank()) {
                                Text(p.reversalReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            LtButton(text = "Close", onClick = onDismiss, ghost = true)
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = LtBurgundy) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = valueColor))
    }
}
