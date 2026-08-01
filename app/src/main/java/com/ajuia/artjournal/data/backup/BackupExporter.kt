package com.ajuia.artjournal.data.backup

interface BackupExporter {
    suspend fun exportToJson(
        appVersionName: String,
        appVersionCode: Int
    ): String
}
