package com.ajuia.artjournal.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthenticationApi {
    @POST("api/v1/auth/token/")
    suspend fun login(@Body request: LoginRequest): TokenPairDto

    @POST("api/v1/auth/token/refresh/")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponseDto

    @POST("api/v1/auth/token/logout/")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
}

interface AccountApi {
    @GET("api/v1/auth/me/")
    suspend fun currentUser(): CurrentUserDto
}

interface SchoolsApi {
    @GET("api/v1/schools/")
    suspend fun schools(): List<SchoolDto>
}

interface SyncApi {
    @POST("api/v1/schools/{schoolId}/sync/commands/")
    suspend fun submitCommands(
        @Path("schoolId") schoolId: String,
        @Body batch: SyncCommandBatchDto
    ): SyncCommandBatchResponseDto

    @GET("api/v1/schools/{schoolId}/sync/changes/")
    suspend fun changes(
        @Path("schoolId") schoolId: String,
        @Query("cursor") cursor: String,
        @Query("limit") limit: Int = 100
    ): ChangeFeedResponseDto
}
