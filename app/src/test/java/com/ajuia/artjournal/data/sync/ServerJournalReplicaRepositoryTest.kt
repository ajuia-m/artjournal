package com.ajuia.artjournal.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerJournalReplicaRepositoryTest {
    private lateinit var database: ServerJournalDatabase
    private lateinit var repository: ServerJournalReplicaRepository
    private val ids = ArrayDeque<String>()
    private val scheduledSchools = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ServerJournalDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ServerJournalReplicaRepository(
            database = database,
            uuidSource = UuidSource { ids.removeFirst() },
            clock = EpochMillisSource { 1_722_510_000_000 },
            syncScheduler = SyncRequestScheduler { schoolId -> scheduledSchools.add(schoolId) }
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `stages replica and protocol payload atomically`() = runTest {
        ids.addAll(listOf(CLIENT_ID, OPERATION_ID))

        val staged = repository.stageStateUpsert(
            schoolId = SCHOOL_ID,
            entityId = ENTITY_ID,
            lessonId = LESSON_ID,
            studentId = STUDENT_ID,
            grade = 5,
            isPresent = true,
            isExcusedAbsence = false,
            homeworkPoints = 87,
            comment = "Good progress",
            note = "",
            baseVersion = 3
        )

        assertEquals(ReplicaSyncState.PENDING, database.syncDao().state(ENTITY_ID)?.syncState)
        assertEquals(1, repository.pendingOperationCount(SCHOOL_ID))
        assertEquals(CLIENT_ID, staged.operation.clientId)
        assertEquals(1L, staged.operation.clientSequence)
        assertEquals(3L, staged.operation.baseVersion)
        assertTrue(staged.operation.payloadJson.contains("\"lessonId\":\"$LESSON_ID\""))
        assertTrue(staged.operation.payloadJson.contains("\"homeworkPoints\":87"))
        assertEquals(listOf(SCHOOL_ID), scheduledSchools)
    }

    @Test
    fun `constraint failure rolls back replica sequence and outbox together`() = runTest {
        val dao = database.syncDao()
        dao.insertMetadata(SyncMetadataEntity(SCHOOL_ID, CLIENT_ID))
        dao.insertOperation(
            PendingOperationEntity(
                operationId = "00000000-0000-0000-0000-000000000099",
                clientId = CLIENT_ID,
                clientSequence = 1,
                schoolId = SCHOOL_ID,
                entityType = "student_lesson_state",
                entityId = "00000000-0000-0000-0000-000000000098",
                action = "upsert",
                baseVersion = null,
                payloadJson = "{}",
                createdAtEpochMs = 1
            )
        )
        ids.add(OPERATION_ID)

        val failure = runCatching {
            repository.stageStateUpsert(
                schoolId = SCHOOL_ID,
                entityId = ENTITY_ID,
                lessonId = LESSON_ID,
                studentId = STUDENT_ID,
                grade = null,
                isPresent = false,
                isExcusedAbsence = true,
                homeworkPoints = null,
                comment = "",
                note = "",
                baseVersion = null
            )
        }

        assertTrue(failure.isFailure)
        assertNull(dao.state(ENTITY_ID))
        assertEquals(1L, dao.metadata(SCHOOL_ID)?.nextClientSequence)
        assertEquals(1, dao.pendingOperationCount(SCHOOL_ID))
        assertTrue(scheduledSchools.isEmpty())
    }

    private companion object {
        const val SCHOOL_ID = "00000000-0000-0000-0000-000000000001"
        const val CLIENT_ID = "00000000-0000-0000-0000-000000000002"
        const val ENTITY_ID = "00000000-0000-0000-0000-000000000003"
        const val LESSON_ID = "00000000-0000-0000-0000-000000000004"
        const val STUDENT_ID = "00000000-0000-0000-0000-000000000005"
        const val OPERATION_ID = "00000000-0000-0000-0000-000000000006"
    }
}
