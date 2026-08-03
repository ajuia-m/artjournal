package com.ajuia.artjournal

import android.content.Context
import com.ajuia.artjournal.data.ArtJournalDatabase
import com.ajuia.artjournal.data.LocalJournalRepository
import com.ajuia.artjournal.data.RoomLocalJournalRepository
import com.ajuia.artjournal.data.backup.ArtJournalBackupExporter
import com.ajuia.artjournal.data.backup.BackupExporter
import com.ajuia.artjournal.data.remote.ApiClientFactory
import com.ajuia.artjournal.data.session.AndroidSessionStore
import com.ajuia.artjournal.data.session.ServerSessionRepository
import com.ajuia.artjournal.data.session.SharedPreferencesWorkspacePreferences
import com.ajuia.artjournal.data.session.WorkspacePreferences
import com.ajuia.artjournal.data.sync.ServerJournalDatabase
import com.ajuia.artjournal.data.sync.ServerJournalReplicaRepository

interface AppContainer {
    val localJournalRepository: LocalJournalRepository
    val backupExporter: BackupExporter
    val serverSessionRepository: ServerSessionRepository
    val workspacePreferences: WorkspacePreferences
    val serverJournalReplicaRepository: ServerJournalReplicaRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val applicationContext = context.applicationContext
    private val database by lazy {
        ArtJournalDatabase.getDatabase(applicationContext)
    }
    private val sessionStore by lazy { AndroidSessionStore(applicationContext) }
    private val serverDatabase by lazy { ServerJournalDatabase.getDatabase(applicationContext) }
    private val apiClients by lazy {
        ApiClientFactory.create(
            baseUrl = BuildConfig.ARTJOURNAL_API_BASE_URL,
            sessionStore = sessionStore,
            logRequests = BuildConfig.DEBUG
        )
    }

    override val localJournalRepository: LocalJournalRepository by lazy {
        RoomLocalJournalRepository(database)
    }

    override val backupExporter: BackupExporter by lazy {
        ArtJournalBackupExporter(database)
    }

    override val serverSessionRepository: ServerSessionRepository by lazy {
        ServerSessionRepository(
            authenticationApi = apiClients.authenticationApi,
            accountApi = apiClients.accountApi,
            schoolsApi = apiClients.schoolsApi,
            tokenManager = apiClients.tokenManager,
            sessionStore = sessionStore
        )
    }

    override val workspacePreferences: WorkspacePreferences by lazy {
        SharedPreferencesWorkspacePreferences(applicationContext)
    }

    override val serverJournalReplicaRepository: ServerJournalReplicaRepository by lazy {
        ServerJournalReplicaRepository(serverDatabase)
    }
}
