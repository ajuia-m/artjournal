package com.ajuia.artjournal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajuia.artjournal.data.remote.RemoteFailure
import com.ajuia.artjournal.data.session.ServerSchool
import com.ajuia.artjournal.data.session.ServerSession
import com.ajuia.artjournal.data.session.ServerSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ServerWorkspaceUiState {
    data object LoggedOut : ServerWorkspaceUiState
    data object Loading : ServerWorkspaceUiState
    data class ChooseSchool(
        val session: ServerSession
    ) : ServerWorkspaceUiState
    data class Ready(
        val session: ServerSession
    ) : ServerWorkspaceUiState
    data class Error(
        val message: String,
        val canRetrySessionRestore: Boolean
    ) : ServerWorkspaceUiState
}

class ServerWorkspaceViewModel(
    private val repository: ServerSessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<ServerWorkspaceUiState>(
        ServerWorkspaceUiState.LoggedOut
    )
    val uiState: StateFlow<ServerWorkspaceUiState> = _uiState.asStateFlow()

    private var activated = false

    fun activate() {
        if (activated) return
        activated = true
        restoreSession()
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = ServerWorkspaceUiState.Error(
                message = "Введите логин и пароль.",
                canRetrySessionRestore = false
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = ServerWorkspaceUiState.Loading
            _uiState.value = runCatching { repository.login(username, password) }
                .fold(
                    onSuccess = ::stateForSession,
                    onFailure = { throwable ->
                        ServerWorkspaceUiState.Error(
                            message = throwable.userMessage(),
                            canRetrySessionRestore = false
                        )
                    }
                )
        }
    }

    fun chooseSchool(school: ServerSchool) {
        val session = when (val state = _uiState.value) {
            is ServerWorkspaceUiState.ChooseSchool -> state.session
            is ServerWorkspaceUiState.Ready -> state.session
            else -> return
        }
        _uiState.value = ServerWorkspaceUiState.Ready(
            repository.selectSchool(session, school.id)
        )
    }

    fun showSchoolChooser() {
        val session = (uiState.value as? ServerWorkspaceUiState.Ready)?.session ?: return
        _uiState.value = ServerWorkspaceUiState.ChooseSchool(session)
    }

    fun retrySessionRestore() {
        restoreSession()
    }

    fun showLogin() {
        _uiState.value = ServerWorkspaceUiState.LoggedOut
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = ServerWorkspaceUiState.Loading
            runCatching { repository.logout() }
            _uiState.value = ServerWorkspaceUiState.LoggedOut
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            if (!repository.hasStoredSession()) {
                _uiState.value = ServerWorkspaceUiState.LoggedOut
                return@launch
            }
            _uiState.value = ServerWorkspaceUiState.Loading
            _uiState.value = runCatching { repository.restore() }
                .fold(
                    onSuccess = { session ->
                        session?.let(::stateForSession) ?: ServerWorkspaceUiState.LoggedOut
                    },
                    onFailure = { throwable ->
                        ServerWorkspaceUiState.Error(
                            message = throwable.userMessage(),
                            canRetrySessionRestore = true
                        )
                    }
                )
        }
    }

    private fun stateForSession(session: ServerSession): ServerWorkspaceUiState = when {
        session.schools.isEmpty() -> ServerWorkspaceUiState.Error(
            message = "У пользователя нет доступных школ.",
            canRetrySessionRestore = false
        )
        session.selectedSchool != null -> ServerWorkspaceUiState.Ready(session)
        else -> ServerWorkspaceUiState.ChooseSchool(session)
    }

    private fun Throwable.userMessage(): String = when (this) {
        is RemoteFailure -> message ?: "Ошибка соединения с сервером."
        else -> "Произошла непредвиденная ошибка."
    }
}
