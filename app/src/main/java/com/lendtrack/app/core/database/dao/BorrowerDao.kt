package com.lendtrack.app.core.database.dao

import androidx.room.*
import com.lendtrack.app.core.database.entities.BorrowerEntity
import com.lendtrack.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowerDao {

    @Query("SELECT * FROM borrowers WHERE is_archived = 0 ORDER BY name ASC")
    fun observeActive(): Flow<List<BorrowerEntity>>

    @Query("SELECT * FROM borrowers ORDER BY name ASC")
    fun observeAll(): Flow<List<BorrowerEntity>>

    @Query("""
        SELECT * FROM borrowers
        WHERE is_archived = 0
          AND (name LIKE '%' || :query || '%'
               OR contact_number LIKE '%' || :query || '%'
               OR address LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<BorrowerEntity>>

    @Query("SELECT * FROM borrowers WHERE id = :id")
    suspend fun getById(id: String): BorrowerEntity?

    @Query("SELECT * FROM borrowers WHERE id = :id")
    fun observeById(id: String): Flow<BorrowerEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(borrower: BorrowerEntity)

    @Update
    suspend fun update(borrower: BorrowerEntity)

    @Query("UPDATE borrowers SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE borrowers SET server_id = :serverId, sync_status = :status WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String, status: SyncStatus = SyncStatus.SYNCED)

    @Query("SELECT COUNT(*) FROM borrowers WHERE is_archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM borrowers WHERE sync_status = :status")
    suspend fun getPending(status: SyncStatus = SyncStatus.PENDING): List<BorrowerEntity>

    /** Check for near-duplicate names before inserting */
    @Query("""
        SELECT COUNT(*) FROM borrowers
        WHERE lower(trim(name)) = lower(trim(:name))
          AND is_archived = 0
    """)
    suspend fun countByName(name: String): Int

    @Query("DELETE FROM borrowers WHERE id = :id")
    suspend fun deleteById(id: String)
}
