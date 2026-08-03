package com.ajuia.artjournal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ajuia.artjournal.data.session.ServerSessionRepository

class ServerWorkspaceViewModelFactory(
    private val repository: ServerSessionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ServerWorkspaceViewModel::class.java))
        return ServerWorkspaceViewModel(repository) as T
    }
}
