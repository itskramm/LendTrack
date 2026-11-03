package com.lendtrack.app.core.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncTrigger"

/**
 * Called by repositories after every successful local write.
 *
 * If the device is online, delegates immediately to [FirebaseSyncManager]
 * to push the change. If offline, does nothing — Firebase's own persistence
 * layer queues writes and delivers them when connectivity returns.
 */
@Singleton
class SyncTrigger @Inject constructor(
    private val networkMonitor:    NetworkMonitor,
    private val firebaseSyncManager: FirebaseSyncManager,
) {
    fun onLocalWrite() {
        if (networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "Online — triggering immediate sync after local write.")
            firebaseSyncManager.onLocalWrite()
        } else {
            Log.d(TAG, "Offline — change queued; Firebase will sync when connectivity returns.")
        }
    }
}
