package com.ajuia.artjournal.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class TokenPairDto(
    val access: String,
    val refresh: String
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(val refresh: String)

@JsonClass(generateAdapter = true)
data class RefreshResponseDto(
    val access: String,
    val refresh: String? = null
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(val refresh: String)

@JsonClass(generateAdapter = true)
data class TeachingAssignmentDto(
    val id: String,
    @Json(name = "group_id") val groupId: String,
    @Json(name = "group_name") val groupName: String,
    @Json(name = "subject_id") val subjectId: String? = null,
    @Json(name = "subject_name") val subjectName: String? = null
)

@JsonClass(generateAdapter = true)
data class MembershipDto(
    val id: String,
    @Json(name = "school_id") val schoolId: String,
    @Json(name = "school_name") val schoolName: String,
    @Json(name = "school_slug") val schoolSlug: String,
    val role: String,
    @Json(name = "teaching_assignments")
    val teachingAssignments: List<TeachingAssignmentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CurrentUserDto(
    val id: String,
    val username: String,
    val email: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    val memberships: List<MembershipDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SchoolDto(
    val id: String,
    val name: String,
    val slug: String,
    @Json(name = "default_currency") val defaultCurrency: String
)

@JsonClass(generateAdapter = true)
data class SyncOperationDto(
    val protocolVersion: Int = 1,
    val operationId: String,
    val clientId: String,
    val clientSequence: Long,
    val schoolId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val baseVersion: Long?,
    val dependsOn: List<String>,
    val payload: Map<String, Any?>,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SyncCommandBatchDto(val operations: List<SyncOperationDto>)

@JsonClass(generateAdapter = true)
data class SyncCommandResultDto(
    val operationId: String,
    val status: String,
    val entityType: String,
    val entityId: String,
    val version: Long?,
    val payload: Map<String, Any?>?,
    val error: Map<String, Any?>?
)

@JsonClass(generateAdapter = true)
data class SyncCommandBatchResponseDto(val results: List<SyncCommandResultDto>)

@JsonClass(generateAdapter = true)
data class ChangeEventDto(
    val cursor: String,
    val schoolId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val version: Long,
    val payload: Map<String, Any?>?,
    val actorId: String?,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class ChangeFeedResponseDto(
    val changes: List<ChangeEventDto>,
    val nextCursor: String,
    val hasMore: Boolean
)
