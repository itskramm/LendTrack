package com.lendtrack.app.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores non-sensitive application preferences in DataStore.
 *
 * Sensitive credentials (API keys, auth tokens) are NOT stored here.
 * They go through Android Keystore in [SecurityManager] (Phase 4).
 */
@Singleton
class SyncPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_LAST_SYNC_AT         = stringPreferencesKey("last_sync_at")
        val KEY_LAST_SYNC_CURSOR     = stringPreferencesKey("last_sync_cursor")
        val KEY_BACKEND_BASE_URL     = stringPreferencesKey("backend_base_url")
        val KEY_SYNC_ENABLED         = booleanPreferencesKey("sync_enabled")
        val KEY_PENDING_FAILURES     = intPreferencesKey("pending_failures")
    }

    val lastSyncAt: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_LAST_SYNC_AT] }

    val lastSyncCursor: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_LAST_SYNC_CURSOR] }

    val backendBaseUrl: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_BACKEND_BASE_URL] }

    val syncEnabled: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_SYNC_ENABLED] ?: true }

    suspend fun setLastSyncAt(isoDateTime: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SYNC_AT] = isoDateTime }
    }

    suspend fun setLastSyncCursor(cursor: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SYNC_CURSOR] = cursor }
    }

    suspend fun setBackendBaseUrl(url: String) {
        dataStore.edit { prefs -> prefs[KEY_BACKEND_BASE_URL] = url }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SYNC_ENABLED] = enabled }
    }

    suspend fun incrementFailures() {
        dataStore.edit { prefs ->
            prefs[KEY_PENDING_FAILURES] = (prefs[KEY_PENDING_FAILURES] ?: 0) + 1
        }
    }

    suspend fun resetFailures() {
        dataStore.edit { prefs -> prefs[KEY_PENDING_FAILURES] = 0 }
    }
}
