package com.ajuia.artjournal.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentRiskEvaluatorTest {

    private val evaluator = StudentRiskEvaluator()

    @Test
    fun `two latest completed absences produce a warning`() {
        val snapshot = snapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(id = 3, date = "2026-09-25")
            ),
            states = listOf(
                state(id = 1, lessonId = 1, isPresent = false),
                state(id = 2, lessonId = 2, isPresent = false),
                state(id = 3, lessonId = 3, isPresent = true)
            )
        )

        assertTrue(
            evaluator.hasConsecutiveAbsences(
                snapshot = snapshot,
                studentId = STUDENT_ID,
                groupId = GROUP_ID,
                asOfDate = "2026-09-20"
            )
        )
    }

    @Test
    fun `unmarked latest lesson breaks absence streak`() {
        val snapshot = snapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(id = 3, date = "2026-09-03")
            ),
            states = listOf(
                state(id = 1, lessonId = 1, isPresent = false),
                state(id = 2, lessonId = 2, isPresent = false)
            )
        )

        assertFalse(
            evaluator.hasConsecutiveAbsences(
                snapshot = snapshot,
                studentId = STUDENT_ID,
                groupId = GROUP_ID,
                asOfDate = "2026-09-20"
            )
        )
    }

    @Test
    fun `latest present lesson breaks absence streak`() {
        val snapshot = snapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(id = 3, date = "2026-09-03")
            ),
            states = listOf(
                state(id = 1, lessonId = 1, isPresent = false),
                state(id = 2, lessonId = 2, isPresent = false),
                state(id = 3, lessonId = 3, isPresent = true)
            )
        )

        assertFalse(
            evaluator.hasConsecutiveAbsences(
                snapshot = snapshot,
                studentId = STUDENT_ID,
                groupId = GROUP_ID,
                asOfDate = "2026-09-20"
            )
        )
    }

    @Test
    fun `other groups and non school days do not affect absence streak`() {
        val snapshot = snapshot(
            lessons = listOf(
                lesson(id = 1, date = "2026-09-01"),
                lesson(id = 2, date = "2026-09-02"),
                lesson(
                    id = 3,
                    date = "2026-09-03",
                    isNonSchoolDay = true
                ),
                lesson(id = 4, date = "2026-09-04", groupId = 2)
            ),
            states = listOf(
                state(id = 1, lessonId = 1, isPresent = false),
                state(id = 2, lessonId = 2, isPresent = false),
                state(id = 3, lessonId = 3, isPresent = true),
                state(id = 4, lessonId = 4, isPresent = true)
            )
        )

        assertTrue(
            evaluator.hasConsecutiveAbsences(
                snapshot = snapshot,
                studentId = STUDENT_ID,
                groupId = GROUP_ID,
                asOfDate = "2026-09-20"
            )
        )
    }

    @Test
    fun `stale payment threshold is exclusive`() {
        val exactlyThirtyDays = evaluator.paymentRecordSignal(
            enrollmentDate = "2026-01-01",
            paymentDates = listOf("2026-06-01"),
            asOfDate = "2026-07-01"
        )
        val thirtyOneDays = evaluator.paymentRecordSignal(
            enrollmentDate = "2026-01-01",
            paymentDates = listOf("2026-06-01"),
            asOfDate = "2026-07-02"
        )

        assertFalse(exactlyThirtyDays.isStale)
        assertEquals(30L, exactlyThirtyDays.daysSinceReference)
        assertTrue(thirtyOneDays.isStale)
        assertEquals(31L, thirtyOneDays.daysSinceReference)
    }

    @Test
    fun `payment signal uses latest completed record and ignores future dates`() {
        val result = evaluator.paymentRecordSignal(
            enrollmentDate = "2026-01-01",
            paymentDates = listOf(
                "2026-06-01",
                "2026-06-15",
                "2026-08-01",
                "invalid"
            ),
            asOfDate = "2026-07-20"
        )

        assertEquals("2026-06-15", result.referenceDate)
        assertEquals(35L, result.daysSinceReference)
        assertTrue(result.isStale)
    }

    @Test
    fun `payment signal has no invented fallback date`() {
        val result = evaluator.paymentRecordSignal(
            enrollmentDate = "",
            paymentDates = listOf("invalid", "2026-08-01"),
            asOfDate = "2026-07-20"
        )

        assertNull(result.referenceDate)
        assertNull(result.daysSinceReference)
        assertFalse(result.isStale)
    }

    private fun snapshot(
        lessons: List<AnalyticsLesson>,
        states: List<AnalyticsLessonState>
    ) = AnalyticsSnapshot(
        lessons = lessons,
        lessonStates = states,
        topics = emptyList(),
        topicProgress = emptyList()
    )

    private fun lesson(
        id: Int,
        date: String,
        groupId: Int = GROUP_ID,
        isNonSchoolDay: Boolean = false
    ) = AnalyticsLesson(
        id = id,
        groupId = groupId,
        date = date,
        discipline = "Рисунок",
        isNonSchoolDay = isNonSchoolDay
    )

    private fun state(
        id: Int,
        lessonId: Int,
        isPresent: Boolean
    ) = AnalyticsLessonState(
        id = id,
        studentId = STUDENT_ID,
        lessonId = lessonId,
        grade = null,
        isPresent = isPresent,
        isExcusedAbsence = false,
        homeworkPoints = null
    )

    private companion object {
        const val STUDENT_ID = 7
        const val GROUP_ID = 1
    }
}
