package com.lendtrack.app.feature.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.domain.model.SyncStatus

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val requiresAttentionCount by viewModel.requiresAttentionCount.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val syncStatus   by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isOnline     by viewModel.isOnline.collectAsStateWithLifecycle()
    val themeMode    by viewModel.themeMode.collectAsStateWithLifecycle()
    val immersiveMode by viewModel.immersiveMode.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // Confirm sign-out dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor   = LtSurface,
            shape            = ModalShape,
            title   = { Text("Sign Out?", style = MaterialTheme.typography.headlineMedium) },
            text    = {
                Text(
                    "All locally saved data remains on this tablet. " +
                    "You will need your email and password to sign back in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSignOutDialog = false; onSignOut() },
                    colors  = ButtonDefaults.buttonColors(containerColor = LtRed),
                    shape   = ButtonShape,
                ) { Text("Sign Out", color = Color.White) }
            },
            dismissButton = {
                LtButton(text = "Cancel", onClick = { showSignOutDialog = false }, ghost = true)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Appearance ─────────────────────────────────────────────────────
        LtPanel(title = "Appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeModeButton(
                        label = "Light",
                        selected = themeMode == com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeButton(
                        label = "Dark",
                        selected = themeMode == com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeButton(
                        label = "System",
                        selected = themeMode == com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(com.lendtrack.app.core.preferences.ThemePreferences.ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Screen Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Hide status and navigation bars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = immersiveMode,
                        onCheckedChange = { viewModel.setImmersiveMode(it) }
                    )
                }
            }
        }

        // ── Sync status ────────────────────────────────────────────────────
        LtPanel(title = "Synchronization") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    LtSyncBadge(syncStatus)
                }
                LtButton(
                    text    = if (isSyncing) "Syncing..." else "Force Sync Now",
                    onClick = {
                        isSyncing = true
                        viewModel.forceSyncAll()
                    },
                    icon    = Icons.Filled.Sync,
                    enabled = !isSyncing,
                )
            }

            // Reset syncing state after sync completes (approximate)
            LaunchedEffect(isSyncing) {
                if (isSyncing) {
                    kotlinx.coroutines.delay(2000)
                    isSyncing = false
                }
            }

            if (isSyncing) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LtRose,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            LtDivider()
            Spacer(Modifier.height(12.dp))

            InfoRow(
                label = "Network Status",
                value = if (isOnline) "Online" else "Offline",
                valueColor = if (isOnline) LtSage else LtAmber,
            )
            LtDivider()
            InfoRow(
                label      = "Pending changes",
                value      = if (pendingCount > 0) "$pendingCount" else "None",
                valueColor = if (pendingCount > 0) LtAmber else LtSage,
            )
            LtDivider()
            if (requiresAttentionCount > 0) {
                InfoRow(
                    label      = "Failed operations",
                    value      = "$requiresAttentionCount",
                    valueColor = LtRed,
                )
                LtDivider()
            }
            InfoRow(
                label = "Last successful sync",
                value = lastSyncTime?.take(19)?.replace("T", " ") ?: "Never",
            )
            LtDivider()

            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sync diagnostics", style = MaterialTheme.typography.titleSmall, color = LtBurgundy)
                    Text(
                        "Check Logcat (tag: FirebaseSyncManager) for detailed sync logs showing " +
                        "Firebase connectivity, authentication status, and step-by-step upload progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Tap \"Force Sync Now\" to immediately push all pending changes and see detailed logs.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = LtRose,
                    )
                }
            }
        }

        // ── Sync status legend ─────────────────────────────────────────────
        LtPanel(title = "Sync Status Guide") {
            listOf(
                SyncStatus.SYNCED             to "Confirmed and saved in Firebase.",
                SyncStatus.PENDING            to "Saved locally, waiting to upload.",
                SyncStatus.SYNCING            to "Currently uploading.",
                SyncStatus.FAILED             to "Upload failed — will retry automatically.",
                SyncStatus.REQUIRES_ATTENTION to "Server rejected — needs your review.",
            ).forEach { (status, description) ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    LtSyncBadge(status)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                }
                LtDivider()
            }
        }

        // ── App info ───────────────────────────────────────────────────────
        LtPanel(title = "App Information") {
            InfoRow("Version",    "1.0.0")
            LtDivider()
            InfoRow("Local DB",   "Room (SQLite)")
            LtDivider()
            InfoRow("Cloud DB",   "Firebase Realtime Database")
            LtDivider()
            InfoRow("Auth",       "Firebase Authentication")
            LtDivider()
            InfoRow("Currency",   "Philippine Peso (₱)")
        }

        // ── Account ────────────────────────────────────────────────────────
        LtPanel(title = "Account") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Lender Account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Managed in Firebase Console",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { showSignOutDialog = true },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = LtRed.copy(alpha = 0.1f),
                        contentColor   = LtRed,
                    ),
                    shape = ButtonShape,
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label:      String,
    value:      String,
    valueColor: Color = LtBurgundy,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(color = valueColor, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Text(label)
    }
}
