package com.ajuia.artjournal

import android.content.Context
import com.ajuia.artjournal.data.ArtJournalDatabase
import com.ajuia.artjournal.data.LocalJournalRepository
import com.ajuia.artjournal.data.RoomLocalJournalRepository
import com.ajuia.artjournal.data.backup.ArtJournalBackupExporter
import com.ajuia.artjournal.data.backup.BackupExporter

interface AppContainer {
    val localJournalRepository: LocalJournalRepository
    val backupExporter: BackupExporter
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database by lazy {
        ArtJournalDatabase.getDatabase(context.applicationContext)
    }

    override val localJournalRepository: LocalJournalRepository by lazy {
        RoomLocalJournalRepository(database)
    }

    override val backupExporter: BackupExporter by lazy {
        ArtJournalBackupExporter(database)
    }
}
