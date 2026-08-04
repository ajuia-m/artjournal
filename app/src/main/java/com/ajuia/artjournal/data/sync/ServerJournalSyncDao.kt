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

    @Query("SELECT * FROM pending_operations WHERE operation_id = :operationId")
    suspend fun operation(operationId: String): PendingOperationEntity?

    @Query("SELECT * FROM pending_operations WHERE school_id = :schoolId AND state = 'pending' ORDER BY client_sequence LIMIT :limit")
    suspend fun pendingOperations(schoolId: String, limit: Int): List<PendingOperationEntity>

    @Query("UPDATE pending_operations SET state = 'pending' WHERE school_id = :schoolId AND state = 'sending'")
    suspend fun recoverInterruptedOperations(schoolId: String)

    @Query("UPDATE server_student_lesson_states SET sync_state = 'pending' WHERE school_id = :schoolId AND sync_state = 'syncing'")
    suspend fun recoverInterruptedStates(schoolId: String)

    @Query("UPDATE pending_operations SET state = 'sending', attempt_count = attempt_count + 1, last_attempt_at_epoch_ms = :attemptedAt, error_code = NULL, error_message = NULL WHERE operation_id IN (:operationIds) AND state = 'pending'")
    suspend fun markOperationsSending(operationIds: List<String>, attemptedAt: Long)

    @Query("UPDATE server_student_lesson_states SET sync_state = 'syncing' WHERE id IN (:entityIds) AND sync_state = 'pending'")
    suspend fun markStatesSyncing(entityIds: List<String>)

    @Query("UPDATE pending_operations SET state = 'pending', error_code = :errorCode, error_message = :errorMessage WHERE operation_id IN (:operationIds) AND state = 'sending'")
    suspend fun returnOperationsToPending(
        operationIds: List<String>,
        errorCode: String,
        errorMessage: String
    )

    @Query("UPDATE server_student_lesson_states SET sync_state = 'pending' WHERE id IN (:entityIds) AND sync_state = 'syncing'")
    suspend fun returnStatesToPending(entityIds: List<String>)

    @Query("UPDATE pending_operations SET state = :state, error_code = :errorCode, error_message = :errorMessage WHERE operation_id = :operationId")
    suspend fun markOperationTerminal(
        operationId: String,
        state: String,
        errorCode: String?,
        errorMessage: String?
    )

    @Query("DELETE FROM pending_operations WHERE operation_id = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("SELECT COUNT(*) FROM pending_operations WHERE entity_type = :entityType AND entity_id = :entityId AND state IN ('pending', 'sending', 'blocked', 'conflict', 'rejected')")
    suspend fun activeOperationCount(entityType: String, entityId: String): Int

    @Query("SELECT COUNT(*) FROM pending_operations WHERE school_id = :schoolId AND state = 'pending'")
    suspend fun pendingOperationCount(schoolId: String): Int

    @Query("SELECT * FROM sync_metadata WHERE school_id = :schoolId")
    suspend fun metadata(schoolId: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetadata(metadata: SyncMetadataEntity): Long

    @Query("UPDATE sync_metadata SET next_client_sequence = :nextSequence WHERE school_id = :schoolId")
    suspend fun updateNextSequence(schoolId: String, nextSequence: Long)

    @Query("UPDATE sync_metadata SET cursor = :cursor, last_sync_at_epoch_ms = :syncedAt WHERE school_id = :schoolId")
    suspend fun updateSyncProgress(schoolId: String, cursor: String, syncedAt: Long)

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE operation_id = :operationId")
    suspend fun conflict(operationId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE school_id = :schoolId AND resolved_at_epoch_ms IS NULL ORDER BY created_at_epoch_ms")
    fun observeOpenConflicts(schoolId: String): Flow<List<SyncConflictEntity>>
}
