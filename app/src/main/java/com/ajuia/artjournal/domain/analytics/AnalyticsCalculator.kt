package com.ajuia.artjournal.domain.analytics

class AnalyticsCalculator {

    fun attendance(
        snapshot: AnalyticsSnapshot,
        studentId: Int,
        groupId: Int,
        period: AnalyticsPeriod
    ): AttendanceStats {
        val lessons = relevantLessons(snapshot, groupId, period)
        val statesByLesson = latestStatesByLesson(snapshot, studentId)

        var present = 0
        var absent = 0
        var unmarked = 0
        var excusedAbsences = 0

        lessons.forEach { lesson ->
            val state = statesByLesson[lesson.id]
            when {
                state == null -> unmarked++
                state.isPresent -> present++
                else -> {
                    absent++
                    if (state.isExcusedAbsence) {
                        excusedAbsences++
                    }
                }
            }
        }

        return AttendanceStats(
            present = present,
            absent = absent,
            unmarked = unmarked,
            excusedAbsences = excusedAbsences
        )
    }

    fun homeworkPoints(
        snapshot: AnalyticsSnapshot,
        studentId: Int,
        groupId: Int,
        period: AnalyticsPeriod
    ): Int {
        val lessonIds = relevantLessons(snapshot, groupId, period)
            .mapTo(mutableSetOf()) { it.id }

        return latestStatesByLesson(snapshot, studentId)
            .values
            .filter { it.lessonId in lessonIds }
            .sumOf { it.homeworkPoints ?: 0 }
    }

    fun disciplineScore(
        snapshot: AnalyticsSnapshot,
        studentId: Int,
        groupId: Int,
        discipline: String,
        period: AnalyticsPeriod
    ): DisciplineScore {
        val lessonIds = relevantLessons(snapshot, groupId, period)
            .filter { it.discipline.equals(discipline, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.id }
        val statesByLesson = latestStatesByLesson(snapshot, studentId)
        val periodGradePoints = statesByLesson.values
            .filter { it.lessonId in lessonIds }
            .sumOf { it.grade ?: 0 }

        val topicIds = snapshot.topics
            .filter { topic ->
                topic.discipline.equals(discipline, ignoreCase = true) &&
                    (topic.groupIds.isEmpty() || groupId in topic.groupIds)
            }
            .mapTo(mutableSetOf()) { it.id }
        val lifetimeTopicCriteriaPoints = snapshot.topicProgress
            .filter { it.studentId == studentId && it.topicId in topicIds }
            .groupBy { it.topicId }
            .values
            .sumOf { progressEntries ->
                progressEntries.maxBy { it.id }.criteriaPoints
            }

        return DisciplineScore(
            periodGradePoints = periodGradePoints,
            lifetimeTopicCriteriaPoints = lifetimeTopicCriteriaPoints
        )
    }

    private fun relevantLessons(
        snapshot: AnalyticsSnapshot,
        groupId: Int,
        period: AnalyticsPeriod
    ): List<AnalyticsLesson> = snapshot.lessons.filter { lesson ->
        lesson.groupId == groupId &&
            !lesson.isNonSchoolDay &&
            IsoDate.isWithin(lesson.date, period)
    }

    private fun latestStatesByLesson(
        snapshot: AnalyticsSnapshot,
        studentId: Int
    ): Map<Int, AnalyticsLessonState> = snapshot.lessonStates
        .filter { it.studentId == studentId }
        .groupBy { it.lessonId }
        .mapValues { (_, states) -> states.maxBy { it.id } }
}
