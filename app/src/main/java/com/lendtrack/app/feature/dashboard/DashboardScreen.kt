package com.lendtrack.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToBorrowers: () -> Unit,
    onNavigateToCalendar:  () -> Unit,
    onNavigateToPayments:  () -> Unit,
    onBorrowerClick:       (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val totalOutstanding    by viewModel.totalOutstanding.collectAsStateWithLifecycle()
    val collectedThisMonth  by viewModel.collectedThisMonth.collectAsStateWithLifecycle()
    val activeLoanCount     by viewModel.activeLoanCount.collectAsStateWithLifecycle()
    val activeBorrowerCount by viewModel.activeBorrowerCount.collectAsStateWithLifecycle()
    val pendingSyncCount    by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val upcomingInstallments by viewModel.upcomingInstallments.collectAsStateWithLifecycle()
    val overdueInstallments  by viewModel.overdueInstallments.collectAsStateWithLifecycle()
    val recentPayments       by viewModel.recentPayments.collectAsStateWithLifecycle()
    val recentActivities     by viewModel.recentActivities.collectAsStateWithLifecycle()
    val dueTodayAmount       by viewModel.dueTodayAmount.collectAsStateWithLifecycle()
    val overdueAmount        by viewModel.overdueAmount.collectAsStateWithLifecycle()

    val isTabletLandscape = LocalConfiguration.current.screenWidthDp >= 900

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Sync status banner (when there are pending operations) ──────────
        if (pendingSyncCount > 0) {
            LtSyncBanner(pendingCount = pendingSyncCount)
        }

        // ── Stat cards row ──────────────────────────────────────────────────
        StatCardsGrid(
            isTabletLandscape   = isTabletLandscape,
            totalOutstanding    = totalOutstanding,
            collectedThisMonth  = collectedThisMonth,
            dueTodayAmount      = dueTodayAmount,
            dueTodayCount       = upcomingInstallments.count { it.installment.dueDate == LocalDate.now() },
            overdueAmount       = overdueAmount,
            overdueCount        = overdueInstallments.size,
            activeBorrowerCount = activeBorrowerCount,
            activeLoanCount     = activeLoanCount,
        )

        // ── Two-column layout on landscape tablets ──────────────────────────
        if (isTabletLandscape) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Left: upcoming payments table
                UpcomingPaymentsPanel(
                    installments     = upcomingInstallments,
                    onViewAll        = onNavigateToCalendar,
                    onRecordPayment  = onNavigateToPayments,
                    onBorrowerClick  = onBorrowerClick,
                    modifier         = Modifier.weight(1.25f),
                )
                // Right: mini calendar + recent activity
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RecentActivityPanel(activities = recentActivities)
                    QuickActionsPanel(
                        onAddBorrower   = onNavigateToBorrowers,
                        onRecordPayment = onNavigateToPayments,
                        onViewCalendar  = onNavigateToCalendar,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BalanceOverviewPanel(
                    totalOutstanding   = totalOutstanding,
                    collectedThisMonth = collectedThisMonth,
                    modifier           = Modifier.weight(1.25f),
                )
                RecentPaymentsPanel(
                    payments = recentPayments,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // Single-column stacking for portrait
            UpcomingPaymentsPanel(
                installments    = upcomingInstallments,
                onViewAll       = onNavigateToCalendar,
                onRecordPayment = onNavigateToPayments,
                onBorrowerClick = onBorrowerClick,
            )
            BalanceOverviewPanel(
                totalOutstanding   = totalOutstanding,
                collectedThisMonth = collectedThisMonth,
            )
            RecentActivityPanel(activities = recentActivities)
            RecentPaymentsPanel(payments = recentPayments)
            QuickActionsPanel(
                onAddBorrower   = onNavigateToBorrowers,
                onRecordPayment = onNavigateToPayments,
                onViewCalendar  = onNavigateToCalendar,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat cards grid — mirrors .cards in the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatCardsGrid(
    isTabletLandscape:   Boolean,
    totalOutstanding:    Double,
    collectedThisMonth:  Double,
    dueTodayAmount:      Double,
    dueTodayCount:       Int,
    overdueAmount:       Double,
    overdueCount:        Int,
    activeBorrowerCount: Int,
    activeLoanCount:     Int,
) {
    val cards = listOf(
        StatCardData(
            label    = "Total Outstanding",
            value    = totalOutstanding.peso(),
            subtitle = "Across $activeLoanCount active loan${if (activeLoanCount != 1) "s" else ""}",
            icon     = Icons.Filled.AccountBalance,
        ),
        StatCardData(
            label    = "Collected This Month",
            value    = collectedThisMonth.peso(),
            subtitle = "Total payments received",
            icon     = Icons.Filled.Payments,
        ),
        StatCardData(
            label    = "Due Today",
            value    = dueTodayAmount.peso(),
            subtitle = "$dueTodayCount installment${if (dueTodayCount != 1) "s" else ""}",
            icon     = Icons.Filled.Today,
        ),
        StatCardData(
            label      = "Overdue Payments",
            value      = overdueAmount.peso(),
            subtitle   = "$overdueCount installment${if (overdueCount != 1) "s" else ""}",
            icon       = Icons.Filled.Warning,
            valueColor = LtRed,
        ),
    )

    if (isTabletLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            cards.forEach { card ->
                LtStatCard(
                    label      = card.label,
                    value      = card.value,
                    subtitle   = card.subtitle,
                    valueColor = card.valueColor,
                    icon       = card.icon,
                    modifier   = Modifier.weight(1f),
                )
            }
        }
    } else {
        // 2×2 grid for portrait
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LtStatCard(
                    label    = cards[0].label,
                    value    = cards[0].value,
                    subtitle = cards[0].subtitle,
                    icon     = cards[0].icon,
                    modifier = Modifier.weight(1f),
                )
                LtStatCard(
                    label    = cards[1].label,
                    value    = cards[1].value,
                    subtitle = cards[1].subtitle,
                    icon     = cards[1].icon,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LtStatCard(
                    label    = cards[2].label,
                    value    = cards[2].value,
                    subtitle = cards[2].subtitle,
                    icon     = cards[2].icon,
                    modifier = Modifier.weight(1f),
                )
                LtStatCard(
                    label      = cards[3].label,
                    value      = cards[3].value,
                    subtitle   = cards[3].subtitle,
                    valueColor = LtRed,
                    icon       = cards[3].icon,
                    modifier   = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class StatCardData(
    val label:      String,
    val value:      String,
    val subtitle:   String = "",
    val icon:       ImageVector? = null,
    val valueColor: Color = LtBurgundy,
)

// ─────────────────────────────────────────────────────────────────────────────
// Upcoming payments panel — mirrors .panel with the installments table
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UpcomingPaymentsPanel(
    installments:    List<InstallmentWithBorrower>,
    onViewAll:       () -> Unit,
    onRecordPayment: () -> Unit,
    onBorrowerClick: (borrowerId: String) -> Unit = {},
    modifier:        Modifier = Modifier,
) {
    var query        by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    val today        = LocalDate.now()
    // G-2: installment detail dialog state
    var selectedIwb  by remember { mutableStateOf<InstallmentWithBorrower?>(null) }

    // G-2: show installment detail dialog
    selectedIwb?.let { iwb ->
        InstallmentDetailDialog(
            iwb             = iwb,
            today           = today,
            onDismiss       = { selectedIwb = null },
            onRecordPayment = {
                selectedIwb = null
                onRecordPayment()
            },
        )
    }

    val filtered = installments
        .filter { iwb ->
            (query.isBlank() || iwb.borrower.name.contains(query, ignoreCase = true)) &&
            (statusFilter == "All" || iwb.installment.statusOn(today).label == statusFilter)
        }
        .take(8)

    LtPanel(
        title    = "Upcoming Payments",
        modifier = modifier,
        action   = {
            TextButton(onClick = onViewAll) {
                Text("View All", color = LtRose, style = MaterialTheme.typography.bodySmall)
            }
        },
    ) {
        // Search + filter bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LtSearchField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = "Search borrower…",
                modifier      = Modifier.weight(1f),
                leadingIcon   = Icons.Filled.Search,
            )
            // Status filter dropdown
            var expanded by remember { mutableStateOf(false) }
            Box {
                LtButton(
                    text    = statusFilter,
                    onClick = { expanded = true },
                    ghost   = true,
                    small   = true,
                )
                DropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor   = LtSurface,
                ) {
                    listOf("All", "Upcoming", "Due Soon", "Overdue", "Paid").forEach { s ->
                        DropdownMenuItem(
                            text    = { Text(s, style = MaterialTheme.typography.bodySmall) },
                            onClick = { statusFilter = s; expanded = false },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            LtEmptyState(message = "No upcoming payments match this filter.")
        } else {
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TableHeader("Date",       Modifier.weight(1f))
                TableHeader("Borrower",   Modifier.weight(1.5f))
                TableHeader("Amount Due", Modifier.weight(1f))
                TableHeader("Status",     Modifier.weight(1f))
                TableHeader("",           Modifier.width(80.dp))
            }
            LtDivider()

            filtered.forEach { iwb ->
                InstallmentRow(
                    iwb              = iwb,
                    today            = today,
                    onRecordPayment  = onRecordPayment,
                    onBorrowerClick  = { onBorrowerClick(iwb.borrower.id) },
                    onRowClick       = { selectedIwb = iwb },   // G-2
                )
                LtDivider()
            }
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
        color    = LtBurgundy,
        modifier = modifier,
    )
}

@Composable
private fun InstallmentRow(
    iwb:             InstallmentWithBorrower,
    today:           LocalDate,
    onRecordPayment: () -> Unit,
    onBorrowerClick: () -> Unit = {},
    onRowClick:      () -> Unit = {},        // G-2
) {
    val status = iwb.installment.statusOn(today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)   // G-2: opens InstallmentDetailDialog
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = iwb.installment.dueDate.formatShort(),
            style = MaterialTheme.typography.bodySmall,
            color = LtText,
            modifier = Modifier.weight(1f),
        )
        // Borrower name — tappable link to borrower profile
        Text(
            text  = iwb.borrower.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color      = LtRose,
            ),
            modifier = Modifier.weight(1.5f).clickable(onClick = onBorrowerClick),
        )
        Text(
            text  = iwb.installment.remaining.peso(),
            style = MaterialTheme.typography.bodySmall,
            color = LtText,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1f)) {
            LtStatusBadge(status = status)
        }
        LtButton(
            text    = "Pay",
            onClick = onRecordPayment,
            small   = true,
            modifier = Modifier.width(72.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balance overview — donut chart mirroring the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BalanceOverviewPanel(
    totalOutstanding:   Double,
    collectedThisMonth: Double,
    modifier:           Modifier = Modifier,
) {
    LtPanel(title = "Balance Overview", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Donut chart using a circular progress indicator
            val total  = totalOutstanding + collectedThisMonth
            val fraction = if (total > 0) (collectedThisMonth / total).toFloat().coerceIn(0f, 1f) else 0f

            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress       = { 1f },
                    modifier       = Modifier.fillMaxSize(),
                    color          = LtBlush,
                    strokeWidth    = 22.dp,
                    trackColor     = LtBlush,
                )
                CircularProgressIndicator(
                    progress    = { fraction },
                    modifier    = Modifier.fillMaxSize(),
                    color       = LtRose,
                    strokeWidth = 22.dp,
                    trackColor  = Color.Transparent,
                )
                // Inner circle
                Column(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment  = Alignment.CenterHorizontally,
                    verticalArrangement  = Arrangement.Center,
                ) {
                    Text(
                        text  = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = LtBurgundy,
                    )
                    Text(
                        text  = "repaid",
                        style = MaterialTheme.typography.bodySmall,
                        color = LtMuted,
                    )
                }
            }

            // Legend
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendRow(color = LtBurgundy, label = "Payments Received", value = collectedThisMonth.peso())
                LegendRow(color = LtBlush,    label = "Remaining Balance",  value = totalOutstanding.peso())
            }
        }
        Spacer(Modifier.height(8.dp))
        LtNote("Remaining balance = total payable − payments received.")
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = LtMuted)
            Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = LtBurgundy)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent Activity — mirrors .act rows in the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecentActivityPanel(
    activities: List<com.lendtrack.app.core.database.entities.ActivityLogEntity>,
    modifier:   Modifier = Modifier,
) {
    LtPanel(title = "Recent Activity", modifier = modifier) {
        if (activities.isEmpty()) {
            LtEmptyState("No recent activity.")
        } else {
            activities.forEach { act ->
                ActivityRow(act)
            }
        }
    }
}

@Composable
private fun ActivityRow(act: com.lendtrack.app.core.database.entities.ActivityLogEntity) {
    val (icon, color) = when (act.type) {
        com.lendtrack.app.domain.model.ActivityType.PAYMENT     -> "₱" to LtRose
        com.lendtrack.app.domain.model.ActivityType.WARNING     -> "⚠" to LtRed
        com.lendtrack.app.domain.model.ActivityType.NEW_LOAN    -> "✚" to LtSage
        com.lendtrack.app.domain.model.ActivityType.NEW_BORROWER-> "♡" to LtRose
        com.lendtrack.app.domain.model.ActivityType.ADJUSTMENT  -> "✎" to LtAmber
        com.lendtrack.app.domain.model.ActivityType.SYSTEM      -> "⚙" to LtMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Text(icon, color = color, fontSize = 16.sp, modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(act.description, style = MaterialTheme.typography.bodySmall, color = LtText)
            Text(
                act.occurredAt.toLocalDate().formatFull(),
                style = MaterialTheme.typography.labelSmall,
                color = LtMuted,
            )
        }
    }
    LtDivider()
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent payments panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecentPaymentsPanel(
    payments: List<PaymentWithBorrower>,
    modifier: Modifier = Modifier,
) {
    LtPanel(title = "Recent Transactions", modifier = modifier) {
        if (payments.isEmpty()) {
            LtEmptyState("No recorded payments yet.")
        } else {
            payments.take(6).forEach { pwb ->
                PaymentRow(pwb)
                LtDivider()
            }
        }
    }
}

@Composable
private fun PaymentRow(pwb: PaymentWithBorrower) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("₱", color = LtRose, fontSize = 14.sp)
            }
            Column {
                Text(pwb.borrower.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = LtBurgundy)
                Text(
                    "${pwb.payment.paymentDate.formatShort()} · ${pwb.payment.paymentMethod.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LtMuted,
                )
            }
        }
        Text(
            pwb.payment.amount.peso(),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = LtSage,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick actions panel — mirrors .qa in the prototype
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickActionsPanel(
    onAddBorrower:   () -> Unit,
    onRecordPayment: () -> Unit,
    onViewCalendar:  () -> Unit,
    modifier:        Modifier = Modifier,
) {
    LtPanel(title = "Quick Actions", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LtButton(
                text     = "♡  Add Borrower",
                onClick  = onAddBorrower,
                ghost    = true,
                modifier = Modifier.weight(1f),
            )
            LtButton(
                text     = "₱  Record Payment",
                onClick  = onRecordPayment,
                ghost    = true,
                modifier = Modifier.weight(1f),
            )
            LtButton(
                text     = "▦  View Calendar",
                onClick  = onViewCalendar,
                ghost    = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sync status banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LtSyncBanner(pendingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LtAmber.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Sync, contentDescription = null, tint = LtAmber, modifier = Modifier.size(18.dp))
        Text(
            "$pendingCount change${if (pendingCount != 1) "s" else ""} pending sync with server.",
            style = MaterialTheme.typography.bodySmall.copy(color = LtText),
            modifier = Modifier.weight(1f),
        )
        LtSyncBadge(status = SyncStatus.PENDING)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// G-2: Installment detail dialog — mirrors prototype's "ev" modal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InstallmentDetailDialog(
    iwb:             InstallmentWithBorrower,
    today:           LocalDate,
    onDismiss:       () -> Unit,
    onRecordPayment: () -> Unit,
) {
    val inst   = iwb.installment
    val status = inst.statusOn(today)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = LtSurface,
        shape            = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Installment Details", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = LtMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        iwb.borrower.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color      = LtBurgundy,
                        ),
                    )
                    LtStatusBadge(status)
                }
                HorizontalDivider(color = LtDivider)
                InstDetailRow("Due date",      inst.dueDate.formatFull())
                HorizontalDivider(color = LtDivider)
                InstDetailRow("Amount due",    inst.amount.peso())
                HorizontalDivider(color = LtDivider)
                InstDetailRow("Paid",          inst.amountPaid.peso(), valueColor = LtSage)
                HorizontalDivider(color = LtDivider)
                InstDetailRow(
                    label      = "Remaining",
                    value      = inst.remaining.peso(),
                    valueColor = if (inst.remaining <= 0) LtSage else LtBurgundy,
                )
                HorizontalDivider(color = LtDivider)
                InstDetailRow("Loan balance",  iwb.loan.run { (principal + interestAmount).peso() })
            }
        },
        confirmButton = {
            if (inst.remaining > 0) {
                LtButton(text = "₱  Record Payment", onClick = onRecordPayment)
            }
        },
        dismissButton = {
            LtButton(text = "Close", onClick = onDismiss, ghost = true)
        },
    )
}

@Composable
private fun InstDetailRow(label: String, value: String, valueColor: Color = LtBurgundy) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LtMuted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = valueColor))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ─────────────────────────────────────────────────────────────────────────────

fun Double.peso(): String =
    "₱" + String.format(Locale("en", "PH"), "%,.2f", this)

fun LocalDate.formatShort(): String =
    format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))

fun LocalDate.formatFull(): String =
    format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))

val InstallmentStatus.label: String get() = when (this) {
    InstallmentStatus.PAID     -> "Paid"
    InstallmentStatus.DUE_SOON -> "Due Soon"
    InstallmentStatus.OVERDUE  -> "Overdue"
    InstallmentStatus.UPCOMING -> "Upcoming"
    InstallmentStatus.PARTIAL  -> "Partial"
}
