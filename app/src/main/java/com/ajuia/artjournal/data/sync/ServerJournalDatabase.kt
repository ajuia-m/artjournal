package com.ajuia.artjournal.data.sync

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val SERVER_JOURNAL_DATABASE_NAME = "art_journal_server.db"
const val SERVER_JOURNAL_DATABASE_VERSION = 1

@Database(
    entities = [
        ServerStudentLessonStateEntity::class,
        PendingOperationEntity::class,
        SyncMetadataEntity::class,
        SyncConflictEntity::class
    ],
    version = SERVER_JOURNAL_DATABASE_VERSION,
    exportSchema = true
)
abstract class ServerJournalDatabase : RoomDatabase() {
    abstract fun syncDao(): ServerJournalSyncDao

    companion object {
        @Volatile private var instance: ServerJournalDatabase? = null

        fun getDatabase(context: Context): ServerJournalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ServerJournalDatabase::class.java,
                    SERVER_JOURNAL_DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
