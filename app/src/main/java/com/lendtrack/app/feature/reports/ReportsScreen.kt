package com.lendtrack.app.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.feature.dashboard.peso
import java.time.LocalDate

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    val firstOfMonth = remember { LocalDate.now().withDayOfMonth(1).toString() }
    val today        = remember { LocalDate.now().toString() }

    var rangeFrom        by remember { mutableStateOf(firstOfMonth) }
    var rangeTo          by remember { mutableStateOf(today) }
    var collectedInRange by remember { mutableStateOf(0.0) }

    LaunchedEffect(rangeFrom, rangeTo) {
        if (rangeFrom.isNotBlank() && rangeTo.isNotBlank()) {
            collectedInRange = viewModel.getCollectedInRange(rangeFrom, rangeTo)
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
        // ── Summary stat cards ─────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LtStatCard(
                label    = "Principal Lent",
                value    = summary.totalPrincipal.peso(),
                modifier = Modifier.weight(1f),
            )
            LtStatCard(
                label    = "Outstanding",
                value    = summary.totalOutstanding.peso(),
                modifier = Modifier.weight(1f),
            )
            LtStatCard(
                label      = "Overdue Amount",
                value      = summary.totalOverdue.peso(),
                subtitle   = "${summary.overdueCount} installment${if (summary.overdueCount != 1) "s" else ""}",
                valueColor = LtRed,
                modifier   = Modifier.weight(1f),
            )
        }

        // ── Collections by date range ──────────────────────────────────────
        LtPanel(title = "Collections by Date Range") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LtDatePickerField(
                    label         = "From",
                    value         = rangeFrom,
                    onValueChange = { rangeFrom = it },
                    modifier      = Modifier.weight(1f),
                )
                LtDatePickerField(
                    label         = "To",
                    value         = rangeTo,
                    onValueChange = { rangeTo = it },
                    modifier      = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Collections $rangeFrom – $rangeTo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    collectedInRange.peso(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = LtBurgundy,
                )
            }

            Spacer(Modifier.height(16.dp))

            LtButton(
                text     = "Export CSV",
                onClick  = { viewModel.exportCsv(rangeFrom, rangeTo) },
                icon     = Icons.Filled.FileDownload,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            LtNote(
                "Exports all payments in the selected date range as a CSV file. " +
                "Choose any spreadsheet app to open it."
            )
        }
    }
}
