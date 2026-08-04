package com.ajuia.artjournal.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ajuia.artjournal.data.remote.ChangeEventDto
import com.ajuia.artjournal.data.remote.ChangeFeedResponseDto
import com.ajuia.artjournal.data.remote.SyncApi
import com.ajuia.artjournal.data.remote.SyncCommandBatchDto
import com.ajuia.artjournal.data.remote.SyncCommandBatchResponseDto
import com.ajuia.artjournal.data.remote.SyncCommandResultDto
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerJournalSyncEngineTest {
    private lateinit var database: ServerJournalDatabase
    private lateinit var api: FakeSyncApi
    private lateinit var engine: ServerJournalSyncEngine
    private val ids = ArrayDeque<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ServerJournalDatabase::class.java
        ).allowMainThreadQueries().build()
        api = FakeSyncApi()
        engine = ServerJournalSyncEngine(
            database = database,
            syncApi = api,
            uuidSource = UuidSource { ids.removeFirst() },
            clock = EpochMillisSource { NOW }
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `applied command replaces projection and advances cursor`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = { batch ->
            assertEquals(operation.operationId, batch.operations.single().operationId)
            assertEquals(ReplicaSyncState.SYNCING, database.syncDao().state(ENTITY_ID)?.syncState)
            SyncCommandBatchResponseDto(
                listOf(
                    result(
                        operation,
                        status = "applied",
                        version = 4,
                        payload = payload(grade = 5, comment = "Canonical")
                    )
                )
            )
        }
        api.changePages.add(ChangeFeedResponseDto(emptyList(), "7", false))

        val summary = engine.synchronize(SCHOOL_ID)

        val stored = database.syncDao().state(ENTITY_ID)
        assertEquals(1, summary.submittedOperations)
        assertEquals("7", summary.finalCursor)
        assertEquals(ReplicaSyncState.SYNCED, stored?.syncState)
        assertEquals(4L, stored?.serverVersion)
        assertEquals("Canonical", stored?.comment)
        assertNull(database.syncDao().operation(operation.operationId))
        assertEquals("7", database.syncDao().metadata(SCHOOL_ID)?.cursor)
    }

    @Test
    fun `duplicate command is accepted without leaving outbox entry`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = {
            SyncCommandBatchResponseDto(
                listOf(
                    result(
                        operation,
                        status = "duplicate",
                        version = 3,
                        payload = payload(grade = 4)
                    )
                )
            )
        }

        engine.synchronize(SCHOOL_ID)

        assertNull(database.syncDao().operation(operation.operationId))
        assertEquals(3L, database.syncDao().state(ENTITY_ID)?.serverVersion)
    }

    @Test
    fun `transport failure returns sending command to pending`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = { throw IOException("offline") }

        val failure = runCatching { engine.synchronize(SCHOOL_ID) }

        val stored = database.syncDao().operation(operation.operationId)
        assertTrue(failure.exceptionOrNull() is IOException)
        assertEquals(OutboxState.PENDING, stored?.state)
        assertEquals(1, stored?.attemptCount)
        assertEquals("transport_error", stored?.errorCode)
        assertEquals(ReplicaSyncState.PENDING, database.syncDao().state(ENTITY_ID)?.syncState)
    }

    @Test
    fun `conflict preserves local projection and records server payload`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = {
            SyncCommandBatchResponseDto(
                listOf(
                    result(
                        operation,
                        status = "conflict",
                        version = 9,
                        payload = payload(grade = 2, comment = "Server"),
                        error = error("version_conflict", "Stale base version")
                    )
                )
            )
        }
        api.changePages.add(
            ChangeFeedResponseDto(
                changes = listOf(
                    change(
                        entityId = ENTITY_ID,
                        cursor = "8",
                        version = 9,
                        payload = payload(grade = 2, comment = "Server")
                    )
                ),
                nextCursor = "8",
                hasMore = false
            )
        )

        engine.synchronize(SCHOOL_ID)

        val state = database.syncDao().state(ENTITY_ID)
        val conflict = database.syncDao().conflict(operation.operationId)
        assertEquals(ReplicaSyncState.CONFLICT, state?.syncState)
        assertEquals(5, state?.grade)
        assertEquals(OutboxState.CONFLICT, database.syncDao().operation(operation.operationId)?.state)
        assertEquals(9L, conflict?.serverVersion)
        assertTrue(conflict?.serverPayloadJson?.contains("\"comment\":\"Server\"") == true)
        assertEquals("8", database.syncDao().metadata(SCHOOL_ID)?.cursor)
    }

    @Test
    fun `rejected command remains visible and is not retried`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = {
            SyncCommandBatchResponseDto(
                listOf(
                    result(
                        operation,
                        status = "rejected",
                        error = error("permission_denied", "Assignment was revoked")
                    )
                )
            )
        }

        engine.synchronize(SCHOOL_ID)

        assertEquals(ReplicaSyncState.REJECTED, database.syncDao().state(ENTITY_ID)?.syncState)
        assertEquals(OutboxState.REJECTED, database.syncDao().operation(operation.operationId)?.state)
        assertEquals("permission_denied", database.syncDao().operation(operation.operationId)?.errorCode)
        assertEquals(0, database.syncDao().pendingOperationCount(SCHOOL_ID))
    }

    @Test
    fun `change pages apply remote upsert and tombstone atomically with cursor`() = runTest {
        ids.add(CLIENT_ID)
        database.syncDao().upsertState(
            state(
                entityId = DELETED_ENTITY_ID,
                grade = 3,
                version = 2
            )
        )
        api.changePages.add(
            ChangeFeedResponseDto(
                listOf(change(ENTITY_ID, "1", 1, payload(grade = 4))),
                nextCursor = "1",
                hasMore = true
            )
        )
        api.changePages.add(
            ChangeFeedResponseDto(
                listOf(
                    change(
                        entityId = DELETED_ENTITY_ID,
                        cursor = "2",
                        version = 3,
                        payload = null,
                        action = "delete"
                    )
                ),
                nextCursor = "2",
                hasMore = false
            )
        )

        val summary = engine.synchronize(SCHOOL_ID)

        assertEquals(2, summary.appliedChanges)
        assertEquals(4, database.syncDao().state(ENTITY_ID)?.grade)
        assertTrue(database.syncDao().state(DELETED_ENTITY_ID)?.isDeleted == true)
        assertEquals(3L, database.syncDao().state(DELETED_ENTITY_ID)?.serverVersion)
        assertEquals("2", database.syncDao().metadata(SCHOOL_ID)?.cursor)
    }

    @Test
    fun `invalid result set keeps immutable command pending`() = runTest {
        val operation = stagePendingOperation()
        api.commandHandler = { SyncCommandBatchResponseDto(emptyList()) }

        val failure = runCatching { engine.synchronize(SCHOOL_ID) }

        assertTrue(failure.isFailure)
        assertEquals(OutboxState.PENDING, database.syncDao().operation(operation.operationId)?.state)
        assertEquals("invalid_response", database.syncDao().operation(operation.operationId)?.errorCode)
    }

    @Test
    fun `retry policy retries transient failures but stops revoked session`() {
        assertTrue(IOException("offline").isRetryableSyncFailure(hasStoredSession = true))
        assertFalse(IllegalArgumentException("bad payload").isRetryableSyncFailure(true))
    }

    private suspend fun stagePendingOperation(): PendingOperationEntity {
        ids.addAll(listOf(CLIENT_ID, OPERATION_ID))
        return ServerJournalReplicaRepository(
            database = database,
            uuidSource = UuidSource { ids.removeFirst() },
            clock = EpochMillisSource { NOW }
        ).stageStateUpsert(
            schoolId = SCHOOL_ID,
            entityId = ENTITY_ID,
            lessonId = LESSON_ID,
            studentId = STUDENT_ID,
            grade = 5,
            isPresent = true,
            isExcusedAbsence = false,
            homeworkPoints = 80,
            comment = "Local",
            note = "",
            baseVersion = null
        ).operation
    }

    private fun result(
        operation: PendingOperationEntity,
        status: String,
        version: Long? = null,
        payload: Map<String, Any?>? = null,
        error: Map<String, Any?>? = null
    ) = SyncCommandResultDto(
        operationId = operation.operationId,
        status = status,
        entityType = operation.entityType,
        entityId = operation.entityId,
        version = version,
        payload = payload,
        error = error
    )

    private fun change(
        entityId: String,
        cursor: String,
        version: Long,
        payload: Map<String, Any?>?,
        action: String = "upsert"
    ) = ChangeEventDto(
        cursor = cursor,
        schoolId = SCHOOL_ID,
        entityType = "student_lesson_state",
        entityId = entityId,
        action = action,
        version = version,
        payload = payload,
        actorId = null,
        createdAt = "2026-08-04T10:00:00Z"
    )

    private fun payload(
        grade: Int? = null,
        comment: String = ""
    ): Map<String, Any?> = mapOf(
        "lessonId" to LESSON_ID,
        "studentId" to STUDENT_ID,
        "grade" to grade,
        "isPresent" to true,
        "isExcusedAbsence" to false,
        "homeworkPoints" to 80,
        "comment" to comment,
        "note" to ""
    )

    private fun error(code: String, message: String): Map<String, Any?> =
        mapOf("code" to code, "message" to message, "fields" to emptyMap<String, Any?>())

    private fun state(entityId: String, grade: Int, version: Long) =
        ServerStudentLessonStateEntity(
            id = entityId,
            schoolId = SCHOOL_ID,
            lessonId = LESSON_ID,
            studentId = STUDENT_ID,
            grade = grade,
            serverVersion = version,
            updatedAtEpochMs = NOW
        )

    private class FakeSyncApi : SyncApi {
        var commandHandler: suspend (SyncCommandBatchDto) -> SyncCommandBatchResponseDto = {
            SyncCommandBatchResponseDto(emptyList())
        }
        val changePages = ArrayDeque<ChangeFeedResponseDto>()

        override suspend fun submitCommands(
            schoolId: String,
            batch: SyncCommandBatchDto
        ): SyncCommandBatchResponseDto = commandHandler(batch)

        override suspend fun changes(
            schoolId: String,
            cursor: String,
            limit: Int
        ): ChangeFeedResponseDto = if (changePages.isEmpty()) {
            ChangeFeedResponseDto(emptyList(), cursor, false)
        } else {
            changePages.removeFirst()
        }
    }

    private companion object {
        const val NOW = 1_722_510_000_000L
        const val SCHOOL_ID = "00000000-0000-0000-0000-000000000001"
        const val CLIENT_ID = "00000000-0000-0000-0000-000000000002"
        const val ENTITY_ID = "00000000-0000-0000-0000-000000000003"
        const val LESSON_ID = "00000000-0000-0000-0000-000000000004"
        const val STUDENT_ID = "00000000-0000-0000-0000-000000000005"
        const val OPERATION_ID = "00000000-0000-0000-0000-000000000006"
        const val DELETED_ENTITY_ID = "00000000-0000-0000-0000-000000000007"
    }
}
