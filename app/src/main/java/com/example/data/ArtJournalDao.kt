package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtJournalDao {

    // --- Academic Years ---
    @Query("SELECT * FROM academic_years ORDER BY id DESC")
    fun getAcademicYears(): Flow<List<AcademicYear>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcademicYear(academicYear: AcademicYear): Long

    @Update
    suspend fun updateAcademicYear(academicYear: AcademicYear)

    @Delete
    suspend fun deleteAcademicYear(academicYear: AcademicYear)


    // --- Groups ---
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getGroups(): Flow<List<Group>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)


    // --- Students ---
    @Query("SELECT * FROM students WHERE status != 'deleted' ORDER BY lastName ASC, firstName ASC")
    fun getStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getStudentById(id: Int): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)


    // --- Payments ---
    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)


    // --- Quarters ---
    @Query("SELECT * FROM quarters ORDER BY startDate ASC")
    fun getQuarters(): Flow<List<Quarter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuarter(quarter: Quarter): Long

    @Update
    suspend fun updateQuarter(quarter: Quarter)

    @Delete
    suspend fun deleteQuarter(quarter: Quarter)


    // --- Lessons ---
    @Query("SELECT * FROM lessons ORDER BY date ASC")
    fun getLessons(): Flow<List<Lesson>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: Lesson): Long

    @Update
    suspend fun updateLesson(lesson: Lesson)

    @Delete
    suspend fun deleteLesson(lesson: Lesson)


    // --- Student Lesson States ---
    @Query("SELECT * FROM student_lesson_states")
    fun getStudentLessonStates(): Flow<List<StudentLessonState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentLessonState(state: StudentLessonState): Long

    @Update
    suspend fun updateStudentLessonState(state: StudentLessonState)

    @Delete
    suspend fun deleteStudentLessonState(state: StudentLessonState)

    @Query("DELETE FROM student_lesson_states WHERE lessonId = :lessonId")
    suspend fun deleteStatesByLessonId(lessonId: Int)


    // --- Topics ---
    @Query("SELECT * FROM topics ORDER BY name ASC")
    fun getTopics(): Flow<List<Topic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: Topic): Long

    @Update
    suspend fun updateTopic(topic: Topic)

    @Delete
    suspend fun deleteTopic(topic: Topic)


    // --- Student Topic Progress ---
    @Query("SELECT * FROM student_topic_progress")
    fun getStudentTopicProgress(): Flow<List<StudentTopicProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentTopicProgress(progress: StudentTopicProgress): Long

    @Update
    suspend fun updateStudentTopicProgress(progress: StudentTopicProgress)

    @Delete
    suspend fun deleteStudentTopicProgress(progress: StudentTopicProgress)


    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog): Long

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteAuditLogById(id: Int)

    @Query("DELETE FROM audit_logs WHERE timestamp < :cutoff")
    suspend fun deleteAuditLogsOlderThan(cutoff: Long)
}
