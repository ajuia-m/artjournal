package com.ajuia.artjournal.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ReplicaSyncState {
    const val SYNCED = "synced"
    const val PENDING = "pending"
    const val SYNCING = "syncing"
    const val CONFLICT = "conflict"
    const val REJECTED = "rejected"
}

object OutboxState {
    const val PENDING = "pending"
    const val SENDING = "sending"
    const val BLOCKED = "blocked"
    const val CONFLICT = "conflict"
    const val REJECTED = "rejected"
}

@Entity(
    tableName = "server_student_lesson_states",
    indices = [
        Index(value = ["school_id"]),
        Index(value = ["lesson_id"]),
        Index(value = ["student_id"]),
        Index(value = ["school_id", "lesson_id", "student_id"], unique = true)
    ]
)
data class ServerStudentLessonStateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "school_id") val schoolId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    val grade: Int? = null,
    @ColumnInfo(name = "is_present") val isPresent: Boolean = true,
    @ColumnInfo(name = "is_excused_absence") val isExcusedAbsence: Boolean = false,
    @ColumnInfo(name = "homework_points") val homeworkPoints: Int? = null,
    val comment: String = "",
    val note: String = "",
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = ReplicaSyncState.SYNCED,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["school_id", "state", "client_sequence"]),
        Index(value = ["school_id", "client_id", "client_sequence"], unique = true),
        Index(value = ["entity_type", "entity_id"])
    ]
)
data class PendingOperationEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "protocol_version") val protocolVersion: Int = 1,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "client_sequence") val clientSequence: Long,
    @ColumnInfo(name = "school_id") val schoolId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val action: String,
    @ColumnInfo(name = "base_version") val baseVersion: Long?,
    @ColumnInfo(name = "depends_on_json") val dependsOnJson: String = "[]",
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val state: String = OutboxState.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "last_attempt_at_epoch_ms") val lastAttemptAtEpochMs: Long? = null,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "school_id") val schoolId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "next_client_sequence") val nextClientSequence: Long = 1,
    val cursor: String = "0",
    @ColumnInfo(name = "last_sync_at_epoch_ms") val lastSyncAtEpochMs: Long? = null
)

@Entity(
    tableName = "sync_conflicts",
    indices = [Index(value = ["school_id", "resolved_at_epoch_ms"])]
)
data class SyncConflictEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "school_id") val schoolId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "local_payload_json") val localPayloadJson: String,
    @ColumnInfo(name = "server_payload_json") val serverPayloadJson: String?,
    @ColumnInfo(name = "base_version") val baseVersion: Long?,
    @ColumnInfo(name = "server_version") val serverVersion: Long?,
    @ColumnInfo(name = "error_code") val errorCode: String,
    @ColumnInfo(name = "error_message") val errorMessage: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "resolved_at_epoch_ms") val resolvedAtEpochMs: Long? = null
)
