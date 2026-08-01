package com.ajuia.artjournal.viewmodel

import com.ajuia.artjournal.data.Lesson
import com.ajuia.artjournal.data.StudentLessonState
import com.ajuia.artjournal.data.StudentTopicProgress
import com.ajuia.artjournal.data.Topic
import com.ajuia.artjournal.domain.analytics.AnalyticsLesson
import com.ajuia.artjournal.domain.analytics.AnalyticsLessonState
import com.ajuia.artjournal.domain.analytics.AnalyticsSnapshot
import com.ajuia.artjournal.domain.analytics.AnalyticsTopic
import com.ajuia.artjournal.domain.analytics.AnalyticsTopicProgress

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
