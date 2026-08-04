package com.ajuia.artjournal.data.sync

import androidx.room.withTransaction
import com.ajuia.artjournal.data.remote.ChangeEventDto
import com.ajuia.artjournal.data.remote.SyncApi
import com.ajuia.artjournal.data.remote.SyncCommandBatchDto
import com.ajuia.artjournal.data.remote.SyncCommandResultDto
import com.ajuia.artjournal.data.remote.SyncOperationDto
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SyncRunSummary(
    val submittedOperations: Int,
    val appliedChanges: Int,
    val finalCursor: String
)

class ServerJournalSyncEngine(
    private val database: ServerJournalDatabase,
    private val syncApi: SyncApi,
    private val uuidSource: UuidSource = UuidSource { UUID.randomUUID().toString() },
    private val clock: EpochMillisSource = EpochMillisSource(System::currentTimeMillis),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    private val dao = database.syncDao()
    private val payloadAdapter = moshi.adapter(StudentLessonStateSyncPayload::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val payloadMapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    suspend fun synchronize(schoolId: String): SyncRunSummary {
        require(schoolId.isNotBlank()) { "schoolId must not be blank" }
        database.withTransaction {
            dao.recoverInterruptedOperations(schoolId)
            dao.recoverInterruptedStates(schoolId)
        }
        var submitted = 0
        var commandPages = 0
        while (true) {
            val operations = dao.pendingOperations(schoolId, COMMAND_BATCH_SIZE)
            if (operations.isEmpty()) break
            if (commandPages >= MAX_COMMAND_PAGES) {
                throw JsonDataException("Sync command page limit exceeded.")
            }
            submitOperations(schoolId, operations)
            submitted += operations.size
            commandPages += 1
        }

        var appliedChanges = 0
        var metadata = metadataForSchool(schoolId)
        repeat(MAX_CHANGE_PAGES) {
            val page = syncApi.changes(
                schoolId = schoolId,
                cursor = metadata.cursor,
                limit = CHANGE_PAGE_SIZE
            )
            validateChangePage(schoolId, metadata.cursor, page.nextCursor, page.hasMore)
            database.withTransaction {
                page.changes.forEach { change ->
                    if (applyChange(schoolId, change)) appliedChanges += 1
                }
                dao.updateSyncProgress(schoolId, page.nextCursor, clock.now())
            }
            metadata = requireNotNull(dao.metadata(schoolId))
            if (!page.hasMore) {
                return SyncRunSummary(submitted, appliedChanges, metadata.cursor)
            }
        }
        throw JsonDataException("Sync change page limit exceeded.")
    }

    private suspend fun submitOperations(
        schoolId: String,
        operations: List<PendingOperationEntity>
    ) {
        val operationIds = operations.map(PendingOperationEntity::operationId)
        val entityIds = operations.map(PendingOperationEntity::entityId).distinct()
        database.withTransaction {
            dao.markOperationsSending(operationIds, clock.now())
            dao.markStatesSyncing(entityIds)
        }
        val response = try {
            syncApi.submitCommands(
                schoolId,
                SyncCommandBatchDto(operations.map(::operationDto))
            )
        } catch (throwable: Throwable) {
            resetBatchToPending(
                operationIds,
                entityIds,
                errorCode = "transport_error",
                errorMessage = throwable.javaClass.simpleName
            )
            throw throwable
        }

        try {
            validateCommandResults(operations, response.results)
            database.withTransaction {
                response.results.forEach { result ->
                    val operation = operations.first { it.operationId == result.operationId }
                    applyCommandResult(operation, result)
                }
            }
        } catch (throwable: Throwable) {
            resetBatchToPending(
                operationIds,
                entityIds,
                errorCode = "invalid_response",
                errorMessage = throwable.message ?: throwable.javaClass.simpleName
            )
            throw throwable
        }
    }

    private suspend fun resetBatchToPending(
        operationIds: List<String>,
        entityIds: List<String>,
        errorCode: String,
        errorMessage: String
    ) {
        database.withTransaction {
            dao.returnOperationsToPending(operationIds, errorCode, errorMessage)
            dao.returnStatesToPending(entityIds)
        }
    }

    private fun operationDto(operation: PendingOperationEntity): SyncOperationDto {
        val payload = payloadAdapter.fromJson(operation.payloadJson)
            ?: throw JsonDataException("Operation payload is null.")
        val dependsOn = stringListAdapter.fromJson(operation.dependsOnJson)
            ?: throw JsonDataException("Operation dependencies are null.")
        return SyncOperationDto(
            protocolVersion = operation.protocolVersion,
            operationId = operation.operationId,
            clientId = operation.clientId,
            clientSequence = operation.clientSequence,
            schoolId = operation.schoolId,
            entityType = operation.entityType,
            entityId = operation.entityId,
            action = operation.action,
            baseVersion = operation.baseVersion,
            dependsOn = dependsOn,
            payload = payload.toMap(),
            createdAt = formatUtc(operation.createdAtEpochMs)
        )
    }

    private suspend fun applyCommandResult(
        operation: PendingOperationEntity,
        result: SyncCommandResultDto
    ) {
        when (result.status) {
            "applied", "duplicate" -> applyAcceptedResult(operation, result)
            "conflict" -> applyConflictResult(operation, result)
            "rejected" -> applyRejectedResult(operation, result)
            "blocked" -> applyBlockedResult(operation, result)
            else -> throw JsonDataException("Unsupported command status: ${result.status}")
        }
    }

    private suspend fun applyAcceptedResult(
        operation: PendingOperationEntity,
        result: SyncCommandResultDto
    ) {
        val version = result.version
            ?: throw JsonDataException("Accepted command result has no version.")
        dao.deleteOperation(operation.operationId)
        // A newer local intent for the same entity must remain visible. Its immutable payload is
        // still in the outbox and will either be accepted against its own baseVersion or become an
        // explicit conflict; applying an earlier canonical payload here would silently hide it.
        if (dao.activeOperationCount(operation.entityType, operation.entityId) > 0) return
        val current = dao.state(operation.entityId)
        if (operation.action == "delete") {
            current?.let {
                dao.upsertState(
                    it.copy(
                        serverVersion = version,
                        syncState = ReplicaSyncState.SYNCED,
                        isDeleted = true,
                        updatedAtEpochMs = clock.now()
                    )
                )
            }
        } else {
            val payload = result.payload?.toStatePayload()
                ?: throw JsonDataException("Accepted upsert result has no payload.")
            dao.upsertState(payload.toEntity(operation.schoolId, operation.entityId, version))
        }
    }

    private suspend fun applyConflictResult(
        operation: PendingOperationEntity,
        result: SyncCommandResultDto
    ) {
        val error = result.error.errorDetails("version_conflict", "Server version conflict.")
        dao.markOperationTerminal(
            operation.operationId,
            OutboxState.CONFLICT,
            error.code,
            error.message
        )
        dao.state(operation.entityId)?.let {
            dao.upsertState(
                it.copy(
                    syncState = ReplicaSyncState.CONFLICT,
                    updatedAtEpochMs = clock.now()
                )
            )
        }
        dao.upsertConflict(
            SyncConflictEntity(
                operationId = operation.operationId,
                schoolId = operation.schoolId,
                entityType = operation.entityType,
                entityId = operation.entityId,
                localPayloadJson = operation.payloadJson,
                serverPayloadJson = result.payload?.let(payloadMapAdapter::toJson),
                baseVersion = operation.baseVersion,
                serverVersion = result.version,
                errorCode = error.code,
                errorMessage = error.message,
                createdAtEpochMs = clock.now()
            )
        )
    }

    private suspend fun applyRejectedResult(
        operation: PendingOperationEntity,
        result: SyncCommandResultDto
    ) {
        val error = result.error.errorDetails("rejected", "Server rejected the operation.")
        dao.markOperationTerminal(
            operation.operationId,
            OutboxState.REJECTED,
            error.code,
            error.message
        )
        dao.state(operation.entityId)?.let {
            dao.upsertState(
                it.copy(
                    syncState = ReplicaSyncState.REJECTED,
                    updatedAtEpochMs = clock.now()
                )
            )
        }
    }

    private suspend fun applyBlockedResult(
        operation: PendingOperationEntity,
        result: SyncCommandResultDto
    ) {
        val error = result.error.errorDetails(
            "dependency_not_applied",
            "Operation dependency has not been applied."
        )
        dao.markOperationTerminal(
            operation.operationId,
            OutboxState.BLOCKED,
            error.code,
            error.message
        )
    }

    private suspend fun applyChange(schoolId: String, change: ChangeEventDto): Boolean {
        if (change.schoolId != schoolId) {
            throw JsonDataException("Change event belongs to another school.")
        }
        if (change.entityType != STUDENT_LESSON_STATE) {
            throw JsonDataException("Unsupported change entity type: ${change.entityType}")
        }
        if (change.version < 1) throw JsonDataException("Change version must be positive.")
        if (dao.activeOperationCount(change.entityType, change.entityId) > 0) return false

        val current = dao.state(change.entityId)
        val currentVersion = current?.serverVersion
        if (currentVersion != null && currentVersion >= change.version) return false
        return when (change.action) {
            "upsert" -> {
                val payload = change.payload?.toStatePayload()
                    ?: throw JsonDataException("Upsert change has no payload.")
                dao.upsertState(payload.toEntity(schoolId, change.entityId, change.version))
                true
            }
            "delete" -> {
                current?.let {
                    dao.upsertState(
                        it.copy(
                            serverVersion = change.version,
                            syncState = ReplicaSyncState.SYNCED,
                            isDeleted = true,
                            updatedAtEpochMs = clock.now()
                        )
                    )
                }
                current != null
            }
            else -> throw JsonDataException("Unsupported change action: ${change.action}")
        }
    }

    private suspend fun metadataForSchool(schoolId: String): SyncMetadataEntity =
        database.withTransaction {
            dao.metadata(schoolId)?.let { return@withTransaction it }
            dao.insertMetadata(
                SyncMetadataEntity(
                    schoolId = schoolId,
                    clientId = uuidSource.newUuid()
                )
            )
            dao.metadata(schoolId) ?: error("Failed to initialize sync metadata.")
        }

    private fun StudentLessonStateSyncPayload.toMap(): Map<String, Any?> = mapOf(
        "lessonId" to lessonId,
        "studentId" to studentId,
        "grade" to grade,
        "isPresent" to isPresent,
        "isExcusedAbsence" to isExcusedAbsence,
        "homeworkPoints" to homeworkPoints,
        "comment" to comment,
        "note" to note
    )

    private fun Map<String, Any?>.toStatePayload(): StudentLessonStateSyncPayload =
        StudentLessonStateSyncPayload(
            lessonId = requiredString("lessonId"),
            studentId = requiredString("studentId"),
            grade = nullableInt("grade"),
            isPresent = requiredBoolean("isPresent"),
            isExcusedAbsence = requiredBoolean("isExcusedAbsence"),
            homeworkPoints = nullableInt("homeworkPoints"),
            comment = requiredString("comment"),
            note = requiredString("note")
        )

    private fun StudentLessonStateSyncPayload.toEntity(
        schoolId: String,
        entityId: String,
        version: Long
    ): ServerStudentLessonStateEntity = ServerStudentLessonStateEntity(
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
        serverVersion = version,
        syncState = ReplicaSyncState.SYNCED,
        isDeleted = false,
        updatedAtEpochMs = clock.now()
    )

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: throw JsonDataException("$key must be a string.")

    private fun Map<String, Any?>.requiredBoolean(key: String): Boolean =
        this[key] as? Boolean ?: throw JsonDataException("$key must be a boolean.")

    private fun Map<String, Any?>.nullableInt(key: String): Int? {
        if (!containsKey(key)) throw JsonDataException("$key is required.")
        val value = this[key] ?: return null
        val number = value as? Number ?: throw JsonDataException("$key must be an integer.")
        val long = number.toLong()
        if (number.toDouble() != long.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw JsonDataException("$key must be an integer.")
        }
        return long.toInt()
    }

    private fun Map<String, Any?>?.errorDetails(
        defaultCode: String,
        defaultMessage: String
    ): SyncErrorDetails = SyncErrorDetails(
        code = this?.get("code") as? String ?: defaultCode,
        message = this?.get("message") as? String ?: defaultMessage
    )

    private fun validateCommandResults(
        operations: List<PendingOperationEntity>,
        results: List<SyncCommandResultDto>
    ) {
        val operationIds = operations.map(PendingOperationEntity::operationId)
        val resultIds = results.map(SyncCommandResultDto::operationId)
        if (resultIds.size != resultIds.toSet().size ||
            resultIds.toSet() != operationIds.toSet()
        ) {
            throw JsonDataException("Command results do not match the submitted operations.")
        }
        val operationsById = operations.associateBy(PendingOperationEntity::operationId)
        results.forEach { result ->
            val operation = requireNotNull(operationsById[result.operationId])
            if (result.entityType != operation.entityType || result.entityId != operation.entityId) {
                throw JsonDataException("Command result entity does not match the submitted operation.")
            }
        }
    }

    private fun validateChangePage(
        schoolId: String,
        currentCursor: String,
        nextCursor: String,
        hasMore: Boolean
    ) {
        val current = currentCursor.toLongOrNull()
            ?: throw JsonDataException("Stored cursor is invalid for school $schoolId.")
        val next = nextCursor.toLongOrNull()
            ?: throw JsonDataException("Server cursor is invalid for school $schoolId.")
        if (next < current || (hasMore && next == current)) {
            throw JsonDataException("Server cursor did not advance monotonically.")
        }
    }

    private fun formatUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))

    private data class SyncErrorDetails(val code: String, val message: String)

    private companion object {
        const val STUDENT_LESSON_STATE = "student_lesson_state"
        const val COMMAND_BATCH_SIZE = 100
        const val CHANGE_PAGE_SIZE = 100
        const val MAX_COMMAND_PAGES = 100
        const val MAX_CHANGE_PAGES = 100
    }
}
