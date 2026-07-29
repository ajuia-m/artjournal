package com.example.data.backup

import com.example.data.AcademicYear
import com.example.data.AuditLog
import com.example.data.Group
import com.example.data.Lesson
import com.example.data.Payment
import com.example.data.Quarter
import com.example.data.Student
import com.example.data.StudentLessonState
import com.example.data.StudentTopicProgress
import com.example.data.Topic
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
