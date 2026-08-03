package com.ajuia.artjournal.data.remote

import com.ajuia.artjournal.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

internal sealed interface RefreshOutcome {
    data class Success(val accessToken: String) : RefreshOutcome
    data object NoSession : RefreshOutcome
    data object Rejected : RefreshOutcome
}

internal class SessionTokenManager(
    private val authenticationApi: AuthenticationApi,
    private val sessionStore: SessionStore
) {
    @Volatile
    private var accessToken: String? = null
    private val refreshMutex = Mutex()

    fun currentAccessToken(): String? = accessToken

    fun hasStoredSession(): Boolean = sessionStore.readRefreshToken() != null

    fun acceptTokenPair(tokens: TokenPairDto) {
        accessToken = tokens.access
        sessionStore.writeRefreshToken(tokens.refresh)
    }

    suspend fun refresh(staleAccessToken: String? = null): RefreshOutcome = refreshMutex.withLock {
        val latestAccessToken = accessToken
        if (staleAccessToken != null &&
            latestAccessToken != null &&
            latestAccessToken != staleAccessToken
        ) {
            return RefreshOutcome.Success(latestAccessToken)
        }
        val refreshToken = sessionStore.readRefreshToken() ?: return RefreshOutcome.NoSession

        try {
            val refreshed = authenticationApi.refresh(RefreshRequest(refreshToken))
            accessToken = refreshed.access
            refreshed.refresh?.let(sessionStore::writeRefreshToken)
            RefreshOutcome.Success(refreshed.access)
        } catch (exception: HttpException) {
            if (exception.code() == 400 || exception.code() == 401) {
                clearSession()
                RefreshOutcome.Rejected
            } else {
                throw exception.toRemoteFailure()
            }
        } catch (throwable: Throwable) {
            throw throwable.toRemoteFailure()
        }
    }

    fun clearSession() {
        accessToken = null
        sessionStore.clearRefreshToken()
        sessionStore.clearSelectedSchoolId()
    }
}

internal class BearerTokenInterceptor(
    private val tokenManager: SessionTokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.currentAccessToken()
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header(AUTHORIZATION, "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}

internal class JwtAuthenticator(
    private val tokenManager: SessionTokenManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        val tokenUsedByRequest = response.request.header(AUTHORIZATION)
            ?.removePrefix("Bearer ")
        val latestToken = tokenManager.currentAccessToken()
        if (latestToken != null && latestToken != tokenUsedByRequest) {
            return response.request.withBearerToken(latestToken)
        }

        val refreshed = runBlocking {
            runCatching { tokenManager.refresh(tokenUsedByRequest) }.getOrNull()
        }
        return when (refreshed) {
            is RefreshOutcome.Success -> response.request.withBearerToken(refreshed.accessToken)
            RefreshOutcome.NoSession,
            RefreshOutcome.Rejected,
            null -> null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            count += 1
            priorResponse = priorResponse.priorResponse
        }
        return count
    }

    private fun Request.withBearerToken(token: String): Request = newBuilder()
        .header(AUTHORIZATION, "Bearer $token")
        .build()

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MAX_ATTEMPTS = 2
    }
}
