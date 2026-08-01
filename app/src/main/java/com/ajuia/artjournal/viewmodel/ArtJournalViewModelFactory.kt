package com.ajuia.artjournal.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ajuia.artjournal.data.LocalJournalRepository
import com.ajuia.artjournal.data.backup.BackupExporter

class ArtJournalViewModelFactory(
    private val application: Application,
    private val repository: LocalJournalRepository,
    private val backupExporter: BackupExporter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ArtJournalViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        @Suppress("UNCHECKED_CAST")
        return ArtJournalViewModel(
            application = application,
            repository = repository,
            backupExporter = backupExporter
        ) as T
    }
}
