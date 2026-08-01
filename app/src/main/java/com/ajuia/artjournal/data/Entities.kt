package com.ajuia.artjournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "academic_years")
data class AcademicYear(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // e.g., "2025-2026"
    val holidays: String = "", // Comma-separated "YYYY-MM-DD" formatted holidays
    val isActive: Boolean = false,
    val quarterMarkers: String = "" // Comma-separated starting dates of quarters
)

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val academicYearId: Int,
    val disciplines: String = "Рисунок,Живопись,Композиция", // Comma-separated list of disciplines
    val schedule: String = "" // DayOfWeek(1..7)->Discipline. Format: "1:Рисунок,3:Живопись"
) {
    fun getDisciplinesList(): List<String> {
        if (disciplines.isBlank()) return emptyList()
        return disciplines.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lastName: String,
    val firstName: String,
    val birthday: String = "", // YYYY-MM-DD
    val enrollmentDate: String = "", // YYYY-MM-DD
    val paperPaymentDate: String? = null, // Last payment date for materials
    val paperPaymentAmount: Double? = null,
    val contractNumber: String = "",
    val groupId: Int,
    val status: String = "active", // "active" | "archived" | "deleted"
    val archiveDate: String? = null,
    val archiveReason: String? = null,
    val customFields: String = "" // Custom fields serialized as "Key1:Val1||Key2:Val2"
) {
    fun getCustomFieldsMap(): Map<String, String> {
        if (customFields.isBlank()) return emptyMap()
        return customFields.split("||").mapNotNull {
            val parts = it.split("::", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    val fullName: String get() = "$lastName $firstName"
}

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val comment: String = ""
)

@Entity(tableName = "quarters")
data class Quarter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val academicYearId: Int,
    val name: String, // "I четверть", "II четверть", "I полугодие", "Год"
    val startDate: String, // YYYY-MM-DD
    val endDate: String // YYYY-MM-DD
)

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val date: String, // YYYY-MM-DD
    val discipline: String,
    val topicId: Int? = null, // Bond to a formal topic
    val customTopicName: String? = null,
    val isNonSchoolDay: Boolean = false // Specific group holiday exclusion
) {
    val displayDisciplineAbbreviation: String
        get() = if (discipline.length >= 3) discipline.substring(0, 3) else discipline
}

@Entity(tableName = "student_lesson_states")
data class StudentLessonState(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val lessonId: Int,
    val grade: Int? = null, // 0..5, null if none
    val isPresent: Boolean = true,
    val isExcusedAbsence: Boolean = false, // true=Уважительный, false=Неуважительный
    val homeworkPoints: Int? = null, // 0..101 (where 101 is 101)
    val comment: String? = null, // Замечание (суммируется в профиле)
    val note: String? = null // Заметка с датой (суммируется в профиле)
)

@Entity(tableName = "topics")
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val discipline: String,
    val criteria: String = "", // Name:MaxPoints, comma-separated, e.g. "Композиция:10,Цвет:10,Объем:5"
    val groupIds: String = "", // Comma-separated bound groups, e.g. "1,2,5"
    val quarterIds: String = "" // Comma-separated bound quarters/periods
) {
    fun getCriteriaList(): List<Pair<String, Int>> {
        if (criteria.isBlank()) return emptyList()
        return criteria.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                val maxVal = parts[1].trim().toIntOrNull() ?: 5
                parts[0].trim() to maxVal
            } else null
        }
    }
}

@Entity(tableName = "student_topic_progress")
data class StudentTopicProgress(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val topicId: Int,
    val stage: Int = 0, // 0..100%
    val criteriaGrades: String = "" // CriterionName:Score, comma-separated, e.g. "Композиция:8,Цвет:7"
) {
    fun getGradesMap(): Map<String, Int> {
        if (criteriaGrades.isBlank()) return emptyMap()
        return criteriaGrades.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                val score = parts[1].trim().toIntOrNull() ?: 0
                parts[0].trim() to score
            } else null
        }.toMap()
    }
}

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // Action title
    val details: String, // Description
    val revertData: String? = null // Optional JSON or CSV serialization to trigger an Undo representation
)
