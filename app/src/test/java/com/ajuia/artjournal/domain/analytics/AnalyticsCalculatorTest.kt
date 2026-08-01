package com.ajuia.artjournal.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsCalculatorTest {

    private val calculator = AnalyticsCalculator()
    private val september = AnalyticsPeriod(
        startDate = "2026-09-01",
        endDate = "2026-09-30",
        asOfDate = "2026-09-20"
    )

    @Test
    fun `attendance separates present absent and unmarked lessons`() {
        val snapshot = AnalyticsSnapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(id = 3, date = "2026-09-03"),
                lesson(id = 4, date = "2026-09-25"),
                lesson(id = 5, date = "2026-08-31"),
                lesson(id = 6, date = "2026-09-04", isNonSchoolDay = true),
                lesson(id = 7, date = "2026-09-05", groupId = 2)
            ),
            lessonStates = listOf(
                state(id = 1, lessonId = 1, isPresent = false),
                state(id = 8, lessonId = 1, isPresent = true),
                state(
                    id = 2,
                    lessonId = 2,
                    isPresent = false,
                    isExcusedAbsence = true
                ),
                state(id = 4, lessonId = 4, isPresent = false),
                state(id = 5, lessonId = 5, isPresent = false),
                state(id = 6, lessonId = 6, isPresent = false),
                state(id = 7, lessonId = 7, isPresent = false)
            ),
            topics = emptyList(),
            topicProgress = emptyList()
        )

        val result = calculator.attendance(
            snapshot = snapshot,
            studentId = STUDENT_ID,
            groupId = GROUP_ID,
            period = september
        )

        assertEquals(1, result.present)
        assertEquals(1, result.absent)
        assertEquals(1, result.unmarked)
        assertEquals(1, result.excusedAbsences)
        assertEquals(2, result.marked)
        assertEquals(3, result.total)
        assertEquals(50.0, result.percentage, 0.001)
    }

    @Test
    fun `invalid and reversed dates do not enter attendance period`() {
        val snapshot = AnalyticsSnapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-02-30"),
                lesson(id = 2, date = "not-a-date")
            ),
            lessonStates = emptyList(),
            topics = emptyList(),
            topicProgress = emptyList()
        )

        val result = calculator.attendance(
            snapshot = snapshot,
            studentId = STUDENT_ID,
            groupId = GROUP_ID,
            period = AnalyticsPeriod(
                startDate = "2026-09-30",
                endDate = "2026-09-01",
                asOfDate = "2026-09-20"
            )
        )

        assertEquals(0, result.total)
        assertEquals(0.0, result.percentage, 0.001)
    }

    @Test
    fun `homework uses latest state and filters group period and future`() {
        val snapshot = AnalyticsSnapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(id = 3, date = "2026-09-03", groupId = 2),
                lesson(id = 4, date = "2026-09-25"),
                lesson(id = 5, date = "2026-09-04", isNonSchoolDay = true)
            ),
            lessonStates = listOf(
                state(id = 1, lessonId = 1, homeworkPoints = 2),
                state(id = 9, lessonId = 1, homeworkPoints = 5),
                state(id = 2, lessonId = 2),
                state(id = 3, lessonId = 3, homeworkPoints = 40),
                state(id = 4, lessonId = 4, homeworkPoints = 40),
                state(id = 5, lessonId = 5, homeworkPoints = 40)
            ),
            topics = emptyList(),
            topicProgress = emptyList()
        )

        val result = calculator.homeworkPoints(
            snapshot = snapshot,
            studentId = STUDENT_ID,
            groupId = GROUP_ID,
            period = september
        )

        assertEquals(5, result)
    }

    @Test
    fun `discipline score keeps period grades separate from lifetime topic points`() {
        val snapshot = AnalyticsSnapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-05", discipline = "Рисунок"),
                lesson(id = 2, date = "2026-09-06", discipline = "Живопись"),
                lesson(
                    id = 3,
                    date = "2026-09-07",
                    discipline = "Рисунок",
                    groupId = 2
                ),
                lesson(id = 4, date = "2026-09-25", discipline = "Рисунок"),
                lesson(
                    id = 5,
                    date = "2026-09-08",
                    discipline = "Рисунок",
                    isNonSchoolDay = true
                )
            ),
            lessonStates = listOf(
                state(id = 1, lessonId = 1, grade = 5),
                state(id = 2, lessonId = 2, grade = 4),
                state(id = 3, lessonId = 3, grade = 5),
                state(id = 4, lessonId = 4, grade = 5),
                state(id = 5, lessonId = 5, grade = 5)
            ),
            topics = listOf(
                AnalyticsTopic(id = 10, discipline = "рисунок", groupIds = setOf(1)),
                AnalyticsTopic(id = 11, discipline = "Рисунок", groupIds = emptySet()),
                AnalyticsTopic(id = 12, discipline = "Рисунок", groupIds = setOf(2)),
                AnalyticsTopic(id = 13, discipline = "Живопись", groupIds = setOf(1))
            ),
            topicProgress = listOf(
                AnalyticsTopicProgress(
                    id = 1,
                    studentId = STUDENT_ID,
                    topicId = 10,
                    criteriaPoints = 3
                ),
                AnalyticsTopicProgress(
                    id = 8,
                    studentId = STUDENT_ID,
                    topicId = 10,
                    criteriaPoints = 8
                ),
                AnalyticsTopicProgress(
                    id = 2,
                    studentId = STUDENT_ID,
                    topicId = 11,
                    criteriaPoints = 4
                ),
                AnalyticsTopicProgress(
                    id = 3,
                    studentId = STUDENT_ID,
                    topicId = 12,
                    criteriaPoints = 50
                ),
                AnalyticsTopicProgress(
                    id = 4,
                    studentId = STUDENT_ID,
                    topicId = 13,
                    criteriaPoints = 50
                ),
                AnalyticsTopicProgress(
                    id = 5,
                    studentId = 99,
                    topicId = 10,
                    criteriaPoints = 50
                )
            )
        )

        val result = calculator.disciplineScore(
            snapshot = snapshot,
            studentId = STUDENT_ID,
            groupId = GROUP_ID,
            discipline = "РИСУНОК",
            period = september
        )

        assertEquals(5, result.periodGradePoints)
        assertEquals(12, result.lifetimeTopicCriteriaPoints)
    }

    private fun lesson(
        id: Int,
        date: String,
        groupId: Int = GROUP_ID,
        discipline: String = "Рисунок",
        isNonSchoolDay: Boolean = false
    ) = AnalyticsLesson(
        id = id,
        groupId = groupId,
        date = date,
        discipline = discipline,
        isNonSchoolDay = isNonSchoolDay
    )

    private fun state(
        id: Int,
        lessonId: Int,
        grade: Int? = null,
        isPresent: Boolean = true,
        isExcusedAbsence: Boolean = false,
        homeworkPoints: Int? = null
    ) = AnalyticsLessonState(
        id = id,
        studentId = STUDENT_ID,
        lessonId = lessonId,
        grade = grade,
        isPresent = isPresent,
        isExcusedAbsence = isExcusedAbsence,
        homeworkPoints = homeworkPoints
    )

    private companion object {
        const val STUDENT_ID = 7
        const val GROUP_ID = 1
    }
}
