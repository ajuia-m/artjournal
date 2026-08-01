package com.ajuia.artjournal.data.backup

import androidx.room.withTransaction
import com.ajuia.artjournal.data.ART_JOURNAL_DATABASE_VERSION
import com.ajuia.artjournal.data.ArtJournalBackupDao
import com.ajuia.artjournal.data.ArtJournalDatabase
import java.util.UUID

class ArtJournalBackupExporter(
    private val database: ArtJournalDatabase,
    private val backupDao: ArtJournalBackupDao = database.artJournalBackupDao(),
    private val codec: ArtJournalBackupCodec = ArtJournalBackupCodec(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val exportIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun exportToJson(
        appVersionName: String,
        appVersionCode: Int
    ): String {
        val data = database.withTransaction {
            ArtJournalBackupDataV1(
                academicYears = backupDao.getAcademicYears(),
                groups = backupDao.getGroups(),
                students = backupDao.getStudents(),
                payments = backupDao.getPayments(),
                quarters = backupDao.getQuarters(),
                lessons = backupDao.getLessons(),
                studentLessonStates = backupDao.getStudentLessonStates(),
                topics = backupDao.getTopics(),
                studentTopicProgress = backupDao.getStudentTopicProgress(),
                auditLogs = backupDao.getAuditLogs()
            )
        }

        return codec.encode(
            ArtJournalBackupV1(
                exportId = exportIdFactory(),
                exportedAtEpochMillis = currentTimeMillis(),
                source = ArtJournalBackupSourceV1(
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    roomSchemaVersion = ART_JOURNAL_DATABASE_VERSION
                ),
                data = data
            )
        )
    }
}
