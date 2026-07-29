package com.example.viewmodel

import com.example.data.Lesson
import com.example.data.StudentLessonState
import com.example.data.StudentTopicProgress
import com.example.data.Topic
import com.example.domain.analytics.AnalyticsLesson
import com.example.domain.analytics.AnalyticsLessonState
import com.example.domain.analytics.AnalyticsSnapshot
import com.example.domain.analytics.AnalyticsTopic
import com.example.domain.analytics.AnalyticsTopicProgress

internal fun buildAnalyticsSnapshot(
    lessons: List<Lesson>,
    lessonStates: List<StudentLessonState>,
    topics: List<Topic>,
    topicProgress: List<StudentTopicProgress>
): AnalyticsSnapshot = AnalyticsSnapshot(
    lessons = lessons.map { lesson ->
        AnalyticsLesson(
            id = lesson.id,
            groupId = lesson.groupId,
            date = lesson.date,
            discipline = lesson.discipline,
            isNonSchoolDay = lesson.isNonSchoolDay
        )
    },
    lessonStates = lessonStates.map { state ->
        AnalyticsLessonState(
            id = state.id,
            studentId = state.studentId,
            lessonId = state.lessonId,
            grade = state.grade,
            isPresent = state.isPresent,
            isExcusedAbsence = state.isExcusedAbsence,
            homeworkPoints = state.homeworkPoints
        )
    },
    topics = topics.map { topic ->
        AnalyticsTopic(
            id = topic.id,
            discipline = topic.discipline,
            groupIds = topic.groupIds
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        )
    },
    topicProgress = topicProgress.map { progress ->
        AnalyticsTopicProgress(
            id = progress.id,
            studentId = progress.studentId,
            topicId = progress.topicId,
            criteriaPoints = progress.getGradesMap().values.sum()
        )
    }
)
