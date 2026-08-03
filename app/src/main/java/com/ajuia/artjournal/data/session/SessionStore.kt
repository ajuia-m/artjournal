package com.ajuia.artjournal.data.session

interface SessionStore {
    fun readRefreshToken(): String?
    fun writeRefreshToken(token: String)
    fun clearRefreshToken()
    fun readSelectedSchoolId(): String?
    fun writeSelectedSchoolId(schoolId: String)
    fun clearSelectedSchoolId()
}

enum class WorkspaceMode {
    LOCAL_LEGACY,
    SERVER
}

interface WorkspacePreferences {
    fun readMode(): WorkspaceMode?
    fun writeMode(mode: WorkspaceMode)
    fun clearMode()
}
