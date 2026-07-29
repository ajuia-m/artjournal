package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val ART_JOURNAL_DATABASE_VERSION = 2

@Database(
    entities = [
        AcademicYear::class,
        Group::class,
        Student::class,
        Payment::class,
        Quarter::class,
        Lesson::class,
        StudentLessonState::class,
        Topic::class,
        StudentTopicProgress::class,
        AuditLog::class
    ],
    version = ART_JOURNAL_DATABASE_VERSION,
    exportSchema = true
)
abstract class ArtJournalDatabase : RoomDatabase() {

    abstract fun artJournalDao(): ArtJournalDao
    abstract fun artJournalBackupDao(): ArtJournalBackupDao

    companion object {
        @Volatile
        private var INSTANCE: ArtJournalDatabase? = null

        fun getDatabase(context: Context): ArtJournalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArtJournalDatabase::class.java,
                    "art_journal_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
