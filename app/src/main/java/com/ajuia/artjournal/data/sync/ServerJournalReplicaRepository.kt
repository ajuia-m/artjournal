package com.ajuia.artjournal.data.sync

import androidx.room.withTransaction
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@JsonClass(generateAdapter = true)
data class StudentLessonStateSyncPayload(
    val lessonId: String,
    val studentId: String,
    val grade: Int?,
    val isPresent: Boolean,
    val isExcusedAbsence: Boolean,
    val homeworkPoints: Int?,
    val comment: String,
    val note: String
)

data class StagedStateChange(
    val state: ServerStudentLessonStateEntity,
    val operation: PendingOperationEntity
)

fun interface UuidSource { fun newUuid(): String }
fun interface EpochMillisSource { fun now(): Long }

class ServerJournalReplicaRepository(
    private val database: ServerJournalDatabase,
    private val uuidSource: UuidSource = UuidSource { UUID.randomUUID().toString() },
    private val clock: EpochMillisSource = EpochMillisSource(System::currentTimeMillis),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    private val dao = database.syncDao()
    private val payloadAdapter = moshi.adapter(StudentLessonStateSyncPayload::class.java)

    fun observeStates(schoolId: String): Flow<List<ServerStudentLessonStateEntity>> =
        dao.observeStates(schoolId)

    fun observeOpenConflicts(schoolId: String): Flow<List<SyncConflictEntity>> =
        dao.observeOpenConflicts(schoolId)

    suspend fun pendingOperationCount(schoolId: String): Int =
        dao.pendingOperationCount(schoolId)

    suspend fun stageStateUpsert(
        schoolId: String,
        entityId: String = uuidSource.newUuid(),
        lessonId: String,
        studentId: String,
        grade: Int?,
        isPresent: Boolean,
        isExcusedAbsence: Boolean,
        homeworkPoints: Int?,
        comment: String,
        note: String,
        baseVersion: Long?
    ): StagedStateChange {
        require(grade == null || grade in 0..5) { "grade must be null or between 0 and 5" }
        require(homeworkPoints == null || homeworkPoints in 0..101) {
            "homeworkPoints must be null or between 0 and 101"
        }
        val now = clock.now()
        return database.withTransaction {
            val metadata = metadataForSchool(schoolId)
            val sequence = metadata.nextClientSequence
            dao.updateNextSequence(schoolId, sequence + 1)

            val payload = StudentLessonStateSyncPayload(
                lessonId = lessonId,
                studentId = studentId,
                grade = grade,
                isPresent = isPresent,
                isExcusedAbsence = isExcusedAbsence,
                homeworkPoints = homeworkPoints,
                comment = comment,
                note = note
            )
            val state = ServerStudentLessonStateEntity(
                id = entityId,
                schoolId = schoolId,
                lessonId = lessonId,
                studentId = studentId,
                grade = grade,
                isPresent = isPresent,
                isExcusedAbsence = isExcusedAbsence,
                homeworkPoints = homeworkPoints,
                comment = comment,
                note = note,
                serverVersion = baseVersion,
                syncState = ReplicaSyncState.PENDING,
                updatedAtEpochMs = now
            )
            val operation = PendingOperationEntity(
                operationId = uuidSource.newUuid(),
                clientId = metadata.clientId,
                clientSequence = sequence,
                schoolId = schoolId,
                entityType = "student_lesson_state",
                entityId = entityId,
                action = "upsert",
                baseVersion = baseVersion,
                payloadJson = payloadAdapter.toJson(payload),
                createdAtEpochMs = now
            )
            dao.upsertState(state)
            dao.insertOperation(operation)
            StagedStateChange(state, operation)
        }
    }

    private suspend fun metadataForSchool(schoolId: String): SyncMetadataEntity {
        dao.metadata(schoolId)?.let { return it }
        val created = SyncMetadataEntity(schoolId = schoolId, clientId = uuidSource.newUuid())
        dao.insertMetadata(created)
        return dao.metadata(schoolId) ?: error("Failed to initialize sync metadata")
    }
}
