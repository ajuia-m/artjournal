package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 2,
    exportSchema = false
)
abstract class ArtJournalDatabase : RoomDatabase() {

    abstract fun artJournalDao(): ArtJournalDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
