package com.ajuia.artjournal.data

import kotlinx.coroutines.flow.Flow

class ArtJournalRepository(private val dao: ArtJournalDao) {

    val academicYears: Flow<List<AcademicYear>> = dao.getAcademicYears()
    val groups: Flow<List<Group>> = dao.getGroups()
    val students: Flow<List<Student>> = dao.getStudents()
    val payments: Flow<List<Payment>> = dao.getPayments()
    val quarters: Flow<List<Quarter>> = dao.getQuarters()
    val lessons: Flow<List<Lesson>> = dao.getLessons()
    val studentLessonStates: Flow<List<StudentLessonState>> = dao.getStudentLessonStates()
    val topics: Flow<List<Topic>> = dao.getTopics()
    val studentTopicProgress: Flow<List<StudentTopicProgress>> = dao.getStudentTopicProgress()
    val auditLogs: Flow<List<AuditLog>> = dao.getAuditLogs()

    suspend fun getStudentById(id: Int) = dao.getStudentById(id)

    // Inserts
    suspend fun insertAcademicYear(year: AcademicYear) = dao.insertAcademicYear(year)
    suspend fun insertGroup(group: Group) = dao.insertGroup(group)
    suspend fun insertStudent(student: Student) = dao.insertStudent(student)
    suspend fun insertPayment(payment: Payment) = dao.insertPayment(payment)
    suspend fun insertQuarter(quarter: Quarter) = dao.insertQuarter(quarter)
    suspend fun insertLesson(lesson: Lesson) = dao.insertLesson(lesson)
    suspend fun insertStudentLessonState(state: StudentLessonState) = dao.insertStudentLessonState(state)
    suspend fun insertTopic(topic: Topic) = dao.insertTopic(topic)
    suspend fun insertStudentTopicProgress(progress: StudentTopicProgress) = dao.insertStudentTopicProgress(progress)
    suspend fun insertAuditLog(log: AuditLog) = dao.insertAuditLog(log)

    // Updates
    suspend fun updateAcademicYear(year: AcademicYear) = dao.updateAcademicYear(year)
    suspend fun updateGroup(group: Group) = dao.updateGroup(group)
    suspend fun updateStudent(student: Student) = dao.updateStudent(student)
    suspend fun updatePayment(payment: Payment) = dao.updatePayment(payment)
    suspend fun updateQuarter(quarter: Quarter) = dao.updateQuarter(quarter)
    suspend fun updateLesson(lesson: Lesson) = dao.updateLesson(lesson)
    suspend fun updateStudentLessonState(state: StudentLessonState) = dao.updateStudentLessonState(state)
    suspend fun updateTopic(topic: Topic) = dao.updateTopic(topic)
    suspend fun updateStudentTopicProgress(progress: StudentTopicProgress) = dao.updateStudentTopicProgress(progress)

    // Deletes
    suspend fun deleteAcademicYear(year: AcademicYear) = dao.deleteAcademicYear(year)
    suspend fun deleteGroup(group: Group) = dao.deleteGroup(group)
    suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)
    suspend fun deleteQuarter(quarter: Quarter) = dao.deleteQuarter(quarter)
    suspend fun deleteLesson(lesson: Lesson) {
        dao.deleteStatesByLessonId(lesson.id)
        dao.deleteLesson(lesson)
    }
    suspend fun deleteStudentLessonState(state: StudentLessonState) = dao.deleteStudentLessonState(state)
    suspend fun deleteTopic(topic: Topic) = dao.deleteTopic(topic)
    suspend fun deleteStudentTopicProgress(progress: StudentTopicProgress) = dao.deleteStudentTopicProgress(progress)
    suspend fun deleteAuditLogById(id: Int) = dao.deleteAuditLogById(id)
    suspend fun deleteAuditLogsOlderThan(cutoff: Long) = dao.deleteAuditLogsOlderThan(cutoff)
}
