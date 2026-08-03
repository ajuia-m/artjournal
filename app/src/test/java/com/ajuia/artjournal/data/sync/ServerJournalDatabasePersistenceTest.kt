package com.ajuia.artjournal.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerJournalDatabasePersistenceTest {
    @Test
    fun `pending operation survives database restart`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "server-replica-persistence-test.db"
        context.deleteDatabase(databaseName)
        try {
            val database = open(context, databaseName)
            try {
                database.syncDao().insertOperation(operation())
                assertEquals(1, database.syncDao().pendingOperationCount(SCHOOL_ID))
            } finally {
                database.close()
            }
            val reopened = open(context, databaseName)
            try {
                assertEquals(1, reopened.syncDao().pendingOperationCount(SCHOOL_ID))
                assertEquals(OPERATION_ID, reopened.syncDao().pendingOperations(SCHOOL_ID, 10).single().operationId)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun open(context: Context, name: String) =
        Room.databaseBuilder(context, ServerJournalDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private fun operation() = PendingOperationEntity(
        operationId = OPERATION_ID,
        clientId = "00000000-0000-0000-0000-000000000002",
        clientSequence = 1,
        schoolId = SCHOOL_ID,
        entityType = "student_lesson_state",
        entityId = "00000000-0000-0000-0000-000000000003",
        action = "upsert",
        baseVersion = null,
        payloadJson = "{}",
        createdAtEpochMs = 1
    )

    private companion object {
        const val SCHOOL_ID = "00000000-0000-0000-0000-000000000001"
        const val OPERATION_ID = "00000000-0000-0000-0000-000000000006"
    }
}
