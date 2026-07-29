package com.example.viewmodel

import com.example.data.Lesson
import com.example.data.StudentLessonState
import com.example.data.StudentTopicProgress
import com.example.data.Topic
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsSnapshotMapperTest {

    @Test
    fun `maps persistence entities into deterministic analytics input`() {
        val snapshot = buildAnalyticsSnapshot(
            lessons = listOf(
                Lesson(
                    id = 1,
                    groupId = 2,
                    date = "2026-09-01",
                    discipline = "Рисунок",
                    isNonSchoolDay = false
                )
            ),
            lessonStates = listOf(
                StudentLessonState(
                    id = 3,
                    studentId = 4,
                    lessonId = 1,
                    grade = 5,
                    isPresent = false,
                    isExcusedAbsence = true,
                    homeworkPoints = 8
                )
            ),
            topics = listOf(
                Topic(
                    id = 5,
                    name = "Тон",
                    discipline = "Рисунок",
                    groupIds = "1, bad, 2"
                )
            ),
            topicProgress = listOf(
                StudentTopicProgress(
                    id = 6,
                    studentId = 4,
                    topicId = 5,
                    criteriaGrades = "Композиция:3,Тон:4"
                )
            )
        )

        assertEquals(2, snapshot.lessons.single().groupId)
        assertEquals("2026-09-01", snapshot.lessons.single().date)
        assertEquals(5, snapshot.lessonStates.single().grade)
        assertEquals(false, snapshot.lessonStates.single().isPresent)
        assertEquals(setOf(1, 2), snapshot.topics.single().groupIds)
        assertEquals(7, snapshot.topicProgress.single().criteriaPoints)
    }
}
