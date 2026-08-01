package com.ajuia.artjournal.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ArtJournalBackupDao {

    @Query("SELECT * FROM academic_years ORDER BY id")
    suspend fun getAcademicYears(): List<AcademicYear>

    @Query("SELECT * FROM groups ORDER BY id")
    suspend fun getGroups(): List<Group>

    @Query("SELECT * FROM students ORDER BY id")
    suspend fun getStudents(): List<Student>

    @Query("SELECT * FROM payments ORDER BY id")
    suspend fun getPayments(): List<Payment>

    @Query("SELECT * FROM quarters ORDER BY id")
    suspend fun getQuarters(): List<Quarter>

    @Query("SELECT * FROM lessons ORDER BY id")
    suspend fun getLessons(): List<Lesson>

    @Query("SELECT * FROM student_lesson_states ORDER BY id")
    suspend fun getStudentLessonStates(): List<StudentLessonState>

    @Query("SELECT * FROM topics ORDER BY id")
    suspend fun getTopics(): List<Topic>

    @Query("SELECT * FROM student_topic_progress ORDER BY id")
    suspend fun getStudentTopicProgress(): List<StudentTopicProgress>

    @Query("SELECT * FROM audit_logs ORDER BY id")
    suspend fun getAuditLogs(): List<AuditLog>
}
