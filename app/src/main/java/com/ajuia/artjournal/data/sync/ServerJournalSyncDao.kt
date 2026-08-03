package com.ajuia.artjournal.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerJournalSyncDao {
    @Query("SELECT * FROM server_student_lesson_states WHERE school_id = :schoolId AND is_deleted = 0 ORDER BY updated_at_epoch_ms DESC")
    fun observeStates(schoolId: String): Flow<List<ServerStudentLessonStateEntity>>

    @Query("SELECT * FROM server_student_lesson_states WHERE id = :entityId")
    suspend fun state(entityId: String): ServerStudentLessonStateEntity?

    @Upsert
    suspend fun upsertState(state: ServerStudentLessonStateEntity)

    @Insert
    suspend fun insertOperation(operation: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE school_id = :schoolId AND state = 'pending' ORDER BY client_sequence LIMIT :limit")
    suspend fun pendingOperations(schoolId: String, limit: Int): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE school_id = :schoolId AND state = 'pending'")
    suspend fun pendingOperationCount(schoolId: String): Int

    @Query("SELECT * FROM sync_metadata WHERE school_id = :schoolId")
    suspend fun metadata(schoolId: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetadata(metadata: SyncMetadataEntity): Long

    @Query("UPDATE sync_metadata SET next_client_sequence = :nextSequence WHERE school_id = :schoolId")
    suspend fun updateNextSequence(schoolId: String, nextSequence: Long)

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE school_id = :schoolId AND resolved_at_epoch_ms IS NULL ORDER BY created_at_epoch_ms")
    fun observeOpenConflicts(schoolId: String): Flow<List<SyncConflictEntity>>
}
