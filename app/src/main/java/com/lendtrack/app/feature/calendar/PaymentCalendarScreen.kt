package com.lendtrack.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PaymentCalendarScreen(
    onRecordPayment: (loanId: String) -> Unit = {},
    onBorrowerClick: (borrowerId: String) -> Unit = {},    // G-8
    viewModel:       CalendarViewModel = hiltViewModel(),
) {
    val selectedDate      by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentMonth      by viewModel.currentMonth.collectAsStateWithLifecycle()
    val statusFilter      by viewModel.statusFilter.collectAsStateWithLifecycle()
    val dayInstallments   by viewModel.selectedDayInstallments.collectAsStateWithLifecycle()
    val monthInstallments by viewModel.monthInstallments.collectAsStateWithLifecycle()
    val today             = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        LtPanel(
            title = "Payment Calendar",
            action = {
                LtButton(text = "₱  Record Payment",
                    onClick = { onRecordPayment("") },   // opens record payment with no pre-selected loan
                    small = true)
            },
        ) {
            // Status filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Paid", "Upcoming", "Due Soon", "Overdue").forEach { s ->
                    LtChip(
                        label    = s,
                        selected = statusFilter == s,
                        onClick  = { viewModel.setStatusFilter(s) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Calendar header navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                LtButton(text = "‹", onClick = { viewModel.previousMonth() }, ghost = true, small = true)
                Text(
                    currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.headlineMedium,
                )
                LtButton(text = "›", onClick = { viewModel.nextMonth() }, ghost = true, small = true)
            }

            Spacer(Modifier.height(8.dp))

            // Calendar grid
            LtCalendarGrid(
                currentMonth      = currentMonth,
                selectedDate      = selectedDate,
                today             = today,
                monthInstallments = monthInstallments,
                statusFilter      = statusFilter,
                onDateSelected    = { viewModel.selectDate(it) },
            )

            // Calendar legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    LtSage   to "Paid",
                    LtAmber  to "Due Soon",
                    LtRed    to "Overdue",
                    LtDusty  to "Upcoming",
                ).forEach { (color, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Selected day installments
            Text(
                selectedDate.formatFull(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))

            if (dayInstallments.isEmpty()) {
                LtNote("No payments for this day.")
            } else {
                val filtered = if (statusFilter == "All") dayInstallments
                               else dayInstallments.filter {
                                   it.installment.statusOn(today).label == statusFilter
                               }
                if (filtered.isEmpty()) {
                    LtNote("No $statusFilter payments for this day.")
                } else {
                    filtered.forEach { iwb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                // G-8: borrower name is now a tappable link
                                Text(
                                    iwb.borrower.name,
                                    style    = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color      = LtRose,
                                    ),
                                    modifier = Modifier.clickable { onBorrowerClick(iwb.borrower.id) },
                                )
                                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(iwb.installment.amount.peso(), style = MaterialTheme.typography.bodySmall)
                                LtStatusBadge(iwb.installment.statusOn(today))
                            }
                            if (iwb.installment.remaining > 0) {
                                LtButton(
                                    text    = "Record payment",
                                    onClick = { onRecordPayment(iwb.loan.id) },
                                    small   = true,
                                )
                            }
                        }
                        LtDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun LtCalendarGrid(
    currentMonth:      YearMonth,
    selectedDate:      LocalDate,
    today:             LocalDate,
    monthInstallments: Map<LocalDate, List<InstallmentWithBorrower>>,
    statusFilter:      String,
    onDateSelected:    (LocalDate) -> Unit,
) {
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7  // 0=Sun
    val daysInMonth    = currentMonth.lengthOfMonth()

    // Day headers
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
            Text(
                day,
                textAlign  = TextAlign.Center,
                style      = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                color      = LtRose,
                modifier   = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    // Calendar cells
    val totalCells = firstDayOfWeek + daysInMonth
    val rows = (totalCells + 6) / 7

    repeat(rows) { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(7) { col ->
                val cellIndex = row * 7 + col
                val dayNum    = cellIndex - firstDayOfWeek + 1

                if (cellIndex < firstDayOfWeek || dayNum > daysInMonth) {
                    Box(modifier = Modifier.weight(1f).height(90.dp))
                } else {
                    val date         = currentMonth.atDay(dayNum)
                    val dayInstalls  = monthInstallments[date] ?: emptyList()
                    val filtered     = dayInstalls.filter { iwb ->
                        statusFilter == "All" || iwb.installment.statusOn(today).label == statusFilter
                    }
                    val isSelected   = date == selectedDate
                    val isToday      = date == today

                    CalendarDayCell(
                        dayNum      = dayNum,
                        isSelected  = isSelected,
                        isToday     = isToday,
                        installments = filtered,
                        today       = today,
                        onClick     = { onDateSelected(date) },
                        modifier    = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CalendarDayCell(
    dayNum:       Int,
    isSelected:   Boolean,
    isToday:      Boolean,
    installments: List<InstallmentWithBorrower>,
    today:        LocalDate,
    onClick:      () -> Unit,
    modifier:     Modifier,
) {
    val borderColor = if (isSelected) LtBurgundy else MaterialTheme.colorScheme.surfaceVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bg          = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .height(90.dp)
            .clip(CalendarDayShape)
            .background(bg)
            .border(borderWidth, borderColor, CalendarDayShape)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text  = dayNum.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = if (isToday) LtRose else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            // Show up to 3 event dots
            installments.take(3).forEach { iwb ->
                val status = iwb.installment.statusOn(today)
                val dotColor = when (status) {
                    InstallmentStatus.PAID     -> LtSage
                    InstallmentStatus.DUE_SOON -> LtAmber
                    InstallmentStatus.OVERDUE  -> LtRed
                    else                       -> LtDusty
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(dotColor)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(
                        "₱${iwb.installment.amount.toInt()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color    = Color.White,
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
        }
    }
}
