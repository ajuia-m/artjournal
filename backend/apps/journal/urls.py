from django.urls import path

from apps.journal.views import (
    LessonDetailView,
    LessonListCreateView,
    StudentLessonStateDetailView,
    StudentLessonStateListCreateView,
)

urlpatterns = [
    path("lessons/", LessonListCreateView.as_view(), name="journal-lesson-list"),
    path(
        "lessons/<uuid:lesson_id>/",
        LessonDetailView.as_view(),
        name="journal-lesson-detail",
    ),
    path(
        "lessons/<uuid:lesson_id>/states/",
        StudentLessonStateListCreateView.as_view(),
        name="journal-lesson-state-list",
    ),
    path(
        "lessons/<uuid:lesson_id>/states/<uuid:state_id>/",
        StudentLessonStateDetailView.as_view(),
        name="journal-lesson-state-detail",
    ),
]
