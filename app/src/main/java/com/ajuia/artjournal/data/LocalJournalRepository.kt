package com.ajuia.artjournal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Boundary around the current Room-backed, local-only journal.
 *
 * The entities and integer IDs in this contract deliberately remain legacy
 * persistence types. A server-backed repository will use separate domain
 * models and UUIDs instead of pretending that Room entities are network DTOs.
 */
interface LocalJournalRepository {
    val academicYears: Flow<List<AcademicYear>>
    val groups: Flow<List<Group>>
    val students: Flow<List<Student>>
    val payments: Flow<List<Payment>>
    val quarters: Flow<List<Quarter>>
    val lessons: Flow<List<Lesson>>
    val studentLessonStates: Flow<List<StudentLessonState>>
    val topics: Flow<List<Topic>>
    val studentTopicProgress: Flow<List<StudentTopicProgress>>
    val auditLogs: Flow<List<AuditLog>>

    suspend fun getStudentById(id: Int): Student?

    suspend fun insertAcademicYear(year: AcademicYear): Long
    suspend fun insertGroup(group: Group): Long
    suspend fun insertStudent(student: Student): Long
    suspend fun insertPayment(payment: Payment): Long
    suspend fun insertQuarter(quarter: Quarter): Long
    suspend fun insertLesson(lesson: Lesson): Long
    suspend fun insertStudentLessonState(state: StudentLessonState): Long
    suspend fun insertTopic(topic: Topic): Long
    suspend fun insertStudentTopicProgress(progress: StudentTopicProgress): Long
    suspend fun insertAuditLog(log: AuditLog): Long

    suspend fun updateAcademicYear(year: AcademicYear)
    suspend fun updateGroup(group: Group)
    suspend fun updateStudent(student: Student)
    suspend fun updatePayment(payment: Payment)
    suspend fun updateQuarter(quarter: Quarter)
    suspend fun updateLesson(lesson: Lesson)
    suspend fun updateStudentLessonState(state: StudentLessonState)
    suspend fun updateTopic(topic: Topic)
    suspend fun updateStudentTopicProgress(progress: StudentTopicProgress)

    suspend fun deleteAcademicYear(year: AcademicYear)
    suspend fun deleteGroup(group: Group)
    suspend fun deletePayment(payment: Payment)
    suspend fun deleteQuarter(quarter: Quarter)
    suspend fun deleteLesson(lesson: Lesson)
    suspend fun deleteStudentLessonState(state: StudentLessonState)
    suspend fun deleteTopic(topic: Topic)
    suspend fun deleteStudentTopicProgress(progress: StudentTopicProgress)
    suspend fun deleteAuditLogById(id: Int)
    suspend fun deleteAuditLogsOlderThan(cutoff: Long)
    suspend fun clearAllData()
}

class RoomLocalJournalRepository(
    private val database: ArtJournalDatabase
) : LocalJournalRepository {
    private val dao = database.artJournalDao()

    override val academicYears: Flow<List<AcademicYear>> = dao.getAcademicYears()
    override val groups: Flow<List<Group>> = dao.getGroups()
    override val students: Flow<List<Student>> = dao.getStudents()
    override val payments: Flow<List<Payment>> = dao.getPayments()
    override val quarters: Flow<List<Quarter>> = dao.getQuarters()
    override val lessons: Flow<List<Lesson>> = dao.getLessons()
    override val studentLessonStates: Flow<List<StudentLessonState>> = dao.getStudentLessonStates()
    override val topics: Flow<List<Topic>> = dao.getTopics()
    override val studentTopicProgress: Flow<List<StudentTopicProgress>> = dao.getStudentTopicProgress()
    override val auditLogs: Flow<List<AuditLog>> = dao.getAuditLogs()

    override suspend fun getStudentById(id: Int): Student? = dao.getStudentById(id)

    override suspend fun insertAcademicYear(year: AcademicYear): Long = dao.insertAcademicYear(year)
    override suspend fun insertGroup(group: Group): Long = dao.insertGroup(group)
    override suspend fun insertStudent(student: Student): Long = dao.insertStudent(student)
    override suspend fun insertPayment(payment: Payment): Long = dao.insertPayment(payment)
    override suspend fun insertQuarter(quarter: Quarter): Long = dao.insertQuarter(quarter)
    override suspend fun insertLesson(lesson: Lesson): Long = dao.insertLesson(lesson)
    override suspend fun insertStudentLessonState(state: StudentLessonState): Long = dao.insertStudentLessonState(state)
    override suspend fun insertTopic(topic: Topic): Long = dao.insertTopic(topic)
    override suspend fun insertStudentTopicProgress(progress: StudentTopicProgress): Long = dao.insertStudentTopicProgress(progress)
    override suspend fun insertAuditLog(log: AuditLog): Long = dao.insertAuditLog(log)

    override suspend fun updateAcademicYear(year: AcademicYear) = dao.updateAcademicYear(year)
    override suspend fun updateGroup(group: Group) = dao.updateGroup(group)
    override suspend fun updateStudent(student: Student) = dao.updateStudent(student)
    override suspend fun updatePayment(payment: Payment) = dao.updatePayment(payment)
    override suspend fun updateQuarter(quarter: Quarter) = dao.updateQuarter(quarter)
    override suspend fun updateLesson(lesson: Lesson) = dao.updateLesson(lesson)
    override suspend fun updateStudentLessonState(state: StudentLessonState) = dao.updateStudentLessonState(state)
    override suspend fun updateTopic(topic: Topic) = dao.updateTopic(topic)
    override suspend fun updateStudentTopicProgress(progress: StudentTopicProgress) = dao.updateStudentTopicProgress(progress)

    override suspend fun deleteAcademicYear(year: AcademicYear) = dao.deleteAcademicYear(year)
    override suspend fun deleteGroup(group: Group) = dao.deleteGroup(group)
    override suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)
    override suspend fun deleteQuarter(quarter: Quarter) = dao.deleteQuarter(quarter)

    override suspend fun deleteLesson(lesson: Lesson) {
        dao.deleteStatesByLessonId(lesson.id)
        dao.deleteLesson(lesson)
    }

    override suspend fun deleteStudentLessonState(state: StudentLessonState) = dao.deleteStudentLessonState(state)
    override suspend fun deleteTopic(topic: Topic) = dao.deleteTopic(topic)
    override suspend fun deleteStudentTopicProgress(progress: StudentTopicProgress) = dao.deleteStudentTopicProgress(progress)
    override suspend fun deleteAuditLogById(id: Int) = dao.deleteAuditLogById(id)
    override suspend fun deleteAuditLogsOlderThan(cutoff: Long) = dao.deleteAuditLogsOlderThan(cutoff)

    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }
}
