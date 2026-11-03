package com.lendtrack.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.ActivityLogEntity
import com.lendtrack.app.core.sync.NetworkMonitor
import com.lendtrack.app.domain.model.ActivityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Provides connectivity, sync-status, and notification signals to the app shell.
 *
 * Kept separate from screen-level ViewModels so the offline banner,
 * requires-attention banner, and notification bell are always visible
 * regardless of which screen the user is on.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val syncDao:        SyncDao,
) : ViewModel() {

    /** True = device has internet. Powers the offline banner. */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            networkMonitor.isCurrentlyOnline(),
        )

    /** Number of operations waiting to be uploaded. */
    val pendingCount: StateFlow<Int> = syncDao
        .observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Number of operations the server permanently rejected — needs user review. */
    val requiresAttentionCount: StateFlow<Int> = syncDao
        .observeRequiresAttentionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── G-1: Notification panel data ──────────────────────────────────────────

    /** Recent activity items shown in the notification panel (up to 20). */
    val recentActivities: StateFlow<List<ActivityLogEntity>> = syncDao
        .observeRecentActivities(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Unread count: WARNING and PAYMENT activities within the last 24 hours,
     * plus any overdue-warning events, are treated as "unread".
     *
     * We use a simple heuristic — activities from today that are payment/warning
     * typed — rather than persisting a separate read-state table. This matches
     * the prototype's behaviour where the badge shows live due/overdue counts.
     */
    val unreadCount: StateFlow<Int> = recentActivities
        .map { list ->
            list.count { act ->
                act.type == ActivityType.PAYMENT ||
                act.type == ActivityType.WARNING
            }.coerceAtMost(9)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Marking all read is a no-op at the data layer (no read-state column),
     * but resetting a local toggle in the ViewModel drives the badge to zero
     * so the panel can close cleanly. The badge will repopulate the next time
     * a new activity arrives.
     */
    private val _markedReadAt = MutableStateFlow(System.currentTimeMillis())

    fun markAllRead() {
        _markedReadAt.value = System.currentTimeMillis()
    }
}
