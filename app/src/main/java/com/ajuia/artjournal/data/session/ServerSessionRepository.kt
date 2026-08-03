package com.ajuia.artjournal.data.session

import com.ajuia.artjournal.data.remote.AccountApi
import com.ajuia.artjournal.data.remote.AuthenticationApi
import com.ajuia.artjournal.data.remote.LoginRequest
import com.ajuia.artjournal.data.remote.LogoutRequest
import com.ajuia.artjournal.data.remote.RefreshOutcome
import com.ajuia.artjournal.data.remote.SchoolsApi
import com.ajuia.artjournal.data.remote.SessionTokenManager
import com.ajuia.artjournal.data.remote.toRemoteFailure

data class ServerUser(
    val id: String,
    val username: String,
    val displayName: String
)

data class ServerSchool(
    val id: String,
    val name: String,
    val slug: String,
    val defaultCurrency: String,
    val role: String?
)

data class ServerSession(
    val user: ServerUser,
    val schools: List<ServerSchool>,
    val selectedSchool: ServerSchool?
)

class ServerSessionRepository internal constructor(
    private val authenticationApi: AuthenticationApi,
    private val accountApi: AccountApi,
    private val schoolsApi: SchoolsApi,
    private val tokenManager: SessionTokenManager,
    private val sessionStore: SessionStore
) {
    fun hasStoredSession(): Boolean = tokenManager.hasStoredSession()

    suspend fun login(username: String, password: String): ServerSession {
        val tokens = try {
            authenticationApi.login(LoginRequest(username.trim(), password))
        } catch (throwable: Throwable) {
            throw throwable.toRemoteFailure(loginRequest = true)
        }
        tokenManager.acceptTokenPair(tokens)
        return loadSession()
    }

    suspend fun restore(): ServerSession? {
        if (!hasStoredSession()) return null
        return when (tokenManager.refresh()) {
            is RefreshOutcome.Success -> loadSession()
            RefreshOutcome.NoSession,
            RefreshOutcome.Rejected -> null
        }
    }

    fun selectSchool(session: ServerSession, schoolId: String): ServerSession {
        val school = session.schools.firstOrNull { it.id == schoolId }
            ?: throw IllegalArgumentException("School is not available to the current user.")
        sessionStore.writeSelectedSchoolId(school.id)
        return session.copy(selectedSchool = school)
    }

    suspend fun logout() {
        val refreshToken = sessionStore.readRefreshToken()
        try {
            if (refreshToken != null) {
                authenticationApi.logout(LogoutRequest(refreshToken))
            }
        } finally {
            tokenManager.clearSession()
        }
    }

    private suspend fun loadSession(): ServerSession {
        try {
            val userDto = accountApi.currentUser()
            val membershipRoles = userDto.memberships.associate { it.schoolId to it.role }
            val schools = schoolsApi.schools().map { school ->
                ServerSchool(
                    id = school.id,
                    name = school.name,
                    slug = school.slug,
                    defaultCurrency = school.defaultCurrency,
                    role = membershipRoles[school.id]
                )
            }
            val selectedSchoolId = sessionStore.readSelectedSchoolId()
            val selectedSchool = schools.firstOrNull { it.id == selectedSchoolId }
            if (selectedSchoolId != null && selectedSchool == null) {
                sessionStore.clearSelectedSchoolId()
            }
            val displayName = listOf(userDto.firstName, userDto.lastName)
                .filter(String::isNotBlank)
                .joinToString(" ")
                .ifBlank { userDto.username }
            return ServerSession(
                user = ServerUser(userDto.id, userDto.username, displayName),
                schools = schools,
                selectedSchool = selectedSchool
            )
        } catch (throwable: Throwable) {
            throw throwable.toRemoteFailure()
        }
    }
}
