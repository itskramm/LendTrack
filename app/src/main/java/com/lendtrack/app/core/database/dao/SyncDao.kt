package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.ActivityLogEntity
import com.lendtrack.app.core.database.entities.SyncOperationEntity
import com.lendtrack.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperation(operation: SyncOperationEntity)

    @Update
    suspend fun updateOperation(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE sync_status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOperations(): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE sync_status IN ('PENDING','FAILED') ORDER BY created_at ASC")
    suspend fun getRetryableOperations(): List<SyncOperationEntity>

    /** Count of changes waiting to upload (shown in dashboard badge and Settings) */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE sync_status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /** Count of changes the server permanently rejected — needs user review */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE sync_status = 'REQUIRES_ATTENTION'")
    fun observeRequiresAttentionCount(): Flow<Int>

    /** Total of pending + failed + requires-attention (anything not cleanly synced) */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE sync_status IN ('PENDING','FAILED','REQUIRES_ATTENTION')")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("""
        UPDATE sync_operations
        SET sync_status    = :status,
            attempt_count  = attempt_count + 1,
            last_attempt_at = :now,
            error_message  = :error
        WHERE id = :id
    """)
    suspend fun markAttempt(id: String, status: SyncStatus, now: String, error: String? = null)

    @Query("DELETE FROM sync_operations WHERE sync_status = 'SYNCED'")
    suspend fun deleteSynced()

    // ── Activity log ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityLogEntity)

    @Query("SELECT * FROM activity_log ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecentActivities(limit: Int = 20): Flow<List<ActivityLogEntity>>
}
