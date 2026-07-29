package com.example.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AcademicYear
import com.example.data.ArtJournalDatabase
import com.example.data.AuditLog
import com.example.data.Group
import com.example.data.Lesson
import com.example.data.Payment
import com.example.data.Quarter
import com.example.data.Student
import com.example.data.StudentLessonState
import com.example.data.StudentTopicProgress
import com.example.data.Topic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtJournalBackupExporterTest {

    private lateinit var database: ArtJournalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ArtJournalDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `exports every table and preserves every field`() = runBlocking {
        val dao = database.artJournalDao()

        val year = AcademicYear(
            id = 11,
            name = "2026-2027",
            holidays = "2026-11-04,2027-01-01",
            isActive = true,
            quarterMarkers = "2026-09-01,2026-11-01"
        )
        val group = Group(
            id = 12,
            name = "Группа, А",
            academicYearId = year.id,
            disciplines = "Рисунок,Живопись",
            schedule = "1:Рисунок,3:Живопись"
        )
        val student = Student(
            id = 13,
            lastName = "Иванов",
            firstName = "Иван",
            birthday = "2015-04-12",
            enrollmentDate = "2026-09-01",
            paperPaymentDate = "2026-09-02",
            paperPaymentAmount = 1_500.50,
            contractNumber = "ДОП-13",
            groupId = group.id,
            status = "deleted",
            archiveDate = "2026-10-01",
            archiveReason = "Причина, содержащая запятую",
            customFields = "Ключ::Значение, с запятой||Другой::A:B"
        )
        val payment = Payment(
            id = 14,
            studentId = student.id,
            date = "2026-09-05",
            amount = 3_000.75,
            comment = "Сентябрь, материалы"
        )
        val quarter = Quarter(
            id = 15,
            academicYearId = year.id,
            name = "I четверть",
            startDate = "2026-09-01",
            endDate = "2026-10-25"
        )
        val topic = Topic(
            id = 16,
            name = "Натюрморт, свет",
            discipline = "Живопись",
            criteria = "Композиция:10,Цвет:10",
            groupIds = group.id.toString(),
            quarterIds = quarter.id.toString()
        )
        val lesson = Lesson(
            id = 17,
            groupId = group.id,
            date = "2026-09-07",
            discipline = "Живопись",
            topicId = topic.id,
            customTopicName = "Кувшин, яблоко",
            isNonSchoolDay = false
        )
        val lessonState = StudentLessonState(
            id = 18,
            studentId = student.id,
            lessonId = lesson.id,
            grade = 5,
            isPresent = false,
            isExcusedAbsence = true,
            homeworkPoints = 101,
            comment = "Комментарий, с запятой",
            note = "Заметка || с разделителем"
        )
        val topicProgress = StudentTopicProgress(
            id = 19,
            studentId = student.id,
            topicId = topic.id,
            stage = 73,
            criteriaGrades = "Композиция:8,Цвет:9"
        )
        val auditLog = AuditLog(
            id = 20,
            timestamp = 1_700_000_000_000,
            action = "Проверка",
            details = "Полная запись",
            revertData = "REV:STUDENT_RESTORE||13||active"
        )

        dao.insertAcademicYear(year)
        dao.insertGroup(group)
        dao.insertStudent(student)
        dao.insertPayment(payment)
        dao.insertQuarter(quarter)
        dao.insertTopic(topic)
        dao.insertLesson(lesson)
        dao.insertStudentLessonState(lessonState)
        dao.insertStudentTopicProgress(topicProgress)
        dao.insertAuditLog(auditLog)

        val exporter = ArtJournalBackupExporter(
            database = database,
            currentTimeMillis = { 1_800_000_000_000 },
            exportIdFactory = { "export-id-1" }
        )
        val backup = ArtJournalBackupCodec().decode(
            exporter.exportToJson(
                appVersionName = "1.0",
                appVersionCode = 1
            )
        )

        assertEquals("export-id-1", backup.exportId)
        assertEquals(1_800_000_000_000, backup.exportedAtEpochMillis)
        assertEquals(2, backup.source.roomSchemaVersion)
        assertEquals(listOf(year), backup.data.academicYears)
        assertEquals(listOf(group), backup.data.groups)
        assertEquals(listOf(student), backup.data.students)
        assertEquals(listOf(payment), backup.data.payments)
        assertEquals(listOf(quarter), backup.data.quarters)
        assertEquals(listOf(lesson), backup.data.lessons)
        assertEquals(listOf(lessonState), backup.data.studentLessonStates)
        assertEquals(listOf(topic), backup.data.topics)
        assertEquals(listOf(topicProgress), backup.data.studentTopicProgress)
        assertEquals(listOf(auditLog), backup.data.auditLogs)
    }
}
