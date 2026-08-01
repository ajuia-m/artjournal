package com.ajuia.artjournal.data.backup

import com.ajuia.artjournal.data.AcademicYear
import com.ajuia.artjournal.data.AuditLog
import com.ajuia.artjournal.data.Group
import com.ajuia.artjournal.data.Lesson
import com.ajuia.artjournal.data.Payment
import com.ajuia.artjournal.data.Quarter
import com.ajuia.artjournal.data.Student
import com.ajuia.artjournal.data.StudentLessonState
import com.ajuia.artjournal.data.StudentTopicProgress
import com.ajuia.artjournal.data.Topic
import com.squareup.moshi.JsonClass

const val ART_JOURNAL_BACKUP_FORMAT = "artjournal-backup"
const val ART_JOURNAL_BACKUP_FORMAT_VERSION = 1

@JsonClass(generateAdapter = true)
data class ArtJournalBackupV1(
    val format: String = ART_JOURNAL_BACKUP_FORMAT,
    val formatVersion: Int = ART_JOURNAL_BACKUP_FORMAT_VERSION,
    val exportId: String,
    val exportedAtEpochMillis: Long,
    val source: ArtJournalBackupSourceV1,
    val data: ArtJournalBackupDataV1
)

@JsonClass(generateAdapter = true)
data class ArtJournalBackupSourceV1(
    val platform: String = "android",
    val appVersionName: String,
    val appVersionCode: Int,
    val roomSchemaVersion: Int
)

@JsonClass(generateAdapter = true)
data class ArtJournalBackupDataV1(
    val academicYears: List<AcademicYear>,
    val groups: List<Group>,
    val students: List<Student>,
    val payments: List<Payment>,
    val quarters: List<Quarter>,
    val lessons: List<Lesson>,
    val studentLessonStates: List<StudentLessonState>,
    val topics: List<Topic>,
    val studentTopicProgress: List<StudentTopicProgress>,
    val auditLogs: List<AuditLog>
)
