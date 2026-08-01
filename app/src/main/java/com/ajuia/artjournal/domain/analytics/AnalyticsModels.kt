package com.ajuia.artjournal.domain.analytics

data class AnalyticsPeriod(
    val startDate: String,
    val endDate: String,
    val asOfDate: String
)

data class AnalyticsLesson(
    val id: Int,
    val groupId: Int,
    val date: String,
    val discipline: String,
    val isNonSchoolDay: Boolean
)

data class AnalyticsLessonState(
    val id: Int,
    val studentId: Int,
    val lessonId: Int,
    val grade: Int?,
    val isPresent: Boolean,
    val isExcusedAbsence: Boolean,
    val homeworkPoints: Int?
)

data class AnalyticsTopic(
    val id: Int,
    val discipline: String,
    val groupIds: Set<Int>
)

data class AnalyticsTopicProgress(
    val id: Int,
    val studentId: Int,
    val topicId: Int,
    val criteriaPoints: Int
)

data class AnalyticsSnapshot(
    val lessons: List<AnalyticsLesson>,
    val lessonStates: List<AnalyticsLessonState>,
    val topics: List<AnalyticsTopic>,
    val topicProgress: List<AnalyticsTopicProgress>
) {
    companion object {
        val Empty = AnalyticsSnapshot(
            lessons = emptyList(),
            lessonStates = emptyList(),
            topics = emptyList(),
            topicProgress = emptyList()
        )
    }
}

data class AttendanceStats(
    val present: Int,
    val absent: Int,
    val unmarked: Int,
    val excusedAbsences: Int
) {
    val marked: Int get() = present + absent
    val total: Int get() = marked + unmarked
    val percentage: Double
        get() = if (marked == 0) {
            0.0
        } else {
            present.toDouble() / marked.toDouble() * 100.0
        }
}

data class DisciplineScore(
    val periodGradePoints: Int,
    val lifetimeTopicCriteriaPoints: Int
)

data class PaymentRecordSignal(
    val referenceDate: String?,
    val daysSinceReference: Long?,
    val isStale: Boolean
)
