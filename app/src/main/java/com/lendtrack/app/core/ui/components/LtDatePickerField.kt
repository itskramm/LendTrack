package com.lendtrack.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lendtrack.app.core.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A text field that opens an M3 DatePickerDialog when tapped.
 *
 * [value] and [onValueChange] use ISO-8601 date strings (YYYY-MM-DD),
 * matching the rest of the app's date handling.
 *
 * Falls back to a plain text field if the date string is invalid so the
 * user can always type a date manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtDatePickerField(
    label:         String,
    value:         String,
    onValueChange: (String) -> Unit,
    modifier:      Modifier = Modifier,
    enabled:       Boolean  = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    // Convert current string value to epoch millis for the picker
    val initialMillis = runCatching {
        LocalDate.parse(value)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = value.ifBlank { "Select date" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "Pick date",
                    tint     = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                    showPicker = false
                }) {
                    Text("OK", color = LtRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = LtBurgundy,
                headlineContentColor = LtBurgundy,
                weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedDayContainerColor = LtRose,
                selectedDayContentColor = androidx.compose.ui.graphics.Color.White,
                todayDateBorderColor = LtRose,
                todayContentColor = LtRose,
            ),
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedDayContainerColor = LtRose,
                    todayDateBorderColor = LtRose,
                    todayContentColor = LtRose,
                ),
            )
        }
    }
}
