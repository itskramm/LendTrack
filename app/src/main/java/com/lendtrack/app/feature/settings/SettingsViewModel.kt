package com.lendtrack.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.preferences.ThemePreferences
import com.lendtrack.app.core.sync.FirebaseSyncManager
import com.lendtrack.app.core.sync.NetworkMonitor
import com.lendtrack.app.core.sync.SyncPreferences
import com.lendtrack.app.domain.model.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao:        SyncDao,
    private val prefs:          SyncPreferences,
    private val themePreferences: ThemePreferences,
    private val networkMonitor: NetworkMonitor,
    private val syncManager:    FirebaseSyncManager,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkMonitor.isCurrentlyOnline())

    val pendingCount: StateFlow<Int> = syncDao
        .observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val requiresAttentionCount: StateFlow<Int> = syncDao
        .observeRequiresAttentionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lastSyncTime: StateFlow<String?> = prefs.lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemePreferences.ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePreferences.ThemeMode.SYSTEM
        )

    val immersiveMode: StateFlow<Boolean> = themePreferences.immersiveMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * Derives overall sync status from the pending/attention counts.
     * - Any REQUIRES_ATTENTION → REQUIRES_ATTENTION (needs user to check)
     * - Any PENDING            → PENDING
     * - Online + 0 pending     → SYNCED
     * - Offline + 0 pending    → SYNCED (everything already uploaded)
     */
    val syncStatus: StateFlow<SyncStatus> = combine(
        pendingCount,
        requiresAttentionCount,
    ) { pending, attention ->
        when {
            attention > 0 -> SyncStatus.REQUIRES_ATTENTION
            pending   > 0 -> SyncStatus.PENDING
            else          -> SyncStatus.SYNCED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.SYNCED)

    fun syncNow() {
        viewModelScope.launch {
            syncManager.onLocalWrite()
        }
    }

    fun forceSyncAll() {
        viewModelScope.launch {
            syncManager.onLocalWrite()
        }
    }

    fun setThemeMode(mode: ThemePreferences.ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setImmersiveMode(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setImmersiveMode(enabled)
        }
    }
}
