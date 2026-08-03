package com.ajuia.artjournal.data.remote

import com.ajuia.artjournal.data.session.SessionStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class ApiClients internal constructor(
    val authenticationApi: AuthenticationApi,
    val accountApi: AccountApi,
    val schoolsApi: SchoolsApi,
    val syncApi: SyncApi,
    internal val tokenManager: SessionTokenManager
)

object ApiClientFactory {
    fun create(
        baseUrl: String,
        sessionStore: SessionStore,
        logRequests: Boolean
    ): ApiClients {
        require(baseUrl.endsWith('/')) { "ARTJOURNAL_API_BASE_URL must end with '/'." }

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val logging = HttpLoggingInterceptor().apply {
            level = if (logRequests) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val rawClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val rawRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(rawClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authenticationApi = rawRetrofit.create(AuthenticationApi::class.java)
        val tokenManager = SessionTokenManager(authenticationApi, sessionStore)
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(BearerTokenInterceptor(tokenManager))
            .authenticator(JwtAuthenticator(tokenManager))
            .addInterceptor(logging)
            .build()
        val authenticatedRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authenticatedClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return ApiClients(
            authenticationApi = authenticationApi,
            accountApi = authenticatedRetrofit.create(AccountApi::class.java),
            schoolsApi = authenticatedRetrofit.create(SchoolsApi::class.java),
            syncApi = authenticatedRetrofit.create(SyncApi::class.java),
            tokenManager = tokenManager
        )
    }
}
