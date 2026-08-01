from uuid import UUID

from django.shortcuts import get_object_or_404
from drf_spectacular.utils import extend_schema, extend_schema_view
from rest_framework import generics
from rest_framework.permissions import SAFE_METHODS, IsAuthenticated

from apps.core.serializers import ApiErrorSerializer
from apps.curriculum.models import Subject
from apps.education.models import Group
from apps.journal.models import Lesson, StudentLessonState
from apps.journal.serializers import (
    LessonFilterSerializer,
    LessonSerializer,
    StudentLessonStateSerializer,
)
from apps.schools.access import GroupAction, accessible_subject_ids
from apps.schools.permissions import HasGroupAction

JOURNAL_ERROR_RESPONSES = {
    400: ApiErrorSerializer,
    401: ApiErrorSerializer,
    403: ApiErrorSerializer,
    404: ApiErrorSerializer,
}


class JournalGroupContextMixin:
    permission_classes = (IsAuthenticated, HasGroupAction)

    def get_permissions(self):
        self.required_group_action = (
            GroupAction.VIEW_GROUP
            if self.request.method in SAFE_METHODS
            else GroupAction.EDIT_JOURNAL
        )
        return super().get_permissions()

    def get_group(self) -> Group:
        if not hasattr(self, "_journal_group"):
            self._journal_group = get_object_or_404(
                Group.objects.select_related("academic_year__school"),
                pk=self.kwargs["group_id"],
                academic_year__school_id=self.kwargs["school_id"],
            )
        return self._journal_group

    def get_permission_group(self, request, obj=None) -> Group:
        return self.get_group()

    def get_lesson(self) -> Lesson:
        if not hasattr(self, "_journal_lesson"):
            self._journal_lesson = get_object_or_404(
                Lesson.objects.select_related("group__academic_year__school", "subject"),
                pk=self.kwargs["lesson_id"],
                group=self.get_group(),
            )
        return self._journal_lesson

    def get_permission_subject(self, request, obj=None) -> Subject | None:
        raw_subject_id = None
        is_lesson_view = getattr(self, "serializer_class", None) is LessonSerializer
        if is_lesson_view and request.method == "GET" and "lesson_id" not in self.kwargs:
            raw_subject_id = request.query_params.get("subject_id")
        elif is_lesson_view and request.method not in SAFE_METHODS and hasattr(request.data, "get"):
            raw_subject_id = request.data.get("subject_id")
        if raw_subject_id:
            try:
                subject_id = UUID(str(raw_subject_id))
            except (AttributeError, TypeError, ValueError):
                return None
            return get_object_or_404(
                Subject,
                pk=subject_id,
                school=self.get_group().academic_year.school,
            )
        if isinstance(obj, Lesson):
            return obj.subject
        if isinstance(obj, StudentLessonState):
            return obj.lesson.subject
        if "lesson_id" in self.kwargs:
            return self.get_lesson().subject
        return None


@extend_schema_view(
    get=extend_schema(
        operation_id="journal_lesson_list",
        summary="List accessible lessons in a group",
        tags=["Journal"],
        parameters=[LessonFilterSerializer],
        responses={200: LessonSerializer(many=True), **JOURNAL_ERROR_RESPONSES},
    ),
    post=extend_schema(
        operation_id="journal_lesson_create",
        summary="Create a lesson in a group",
        tags=["Journal"],
        responses={201: LessonSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
)
class LessonListCreateView(JournalGroupContextMixin, generics.ListCreateAPIView):
    queryset = Lesson.objects.all()
    serializer_class = LessonSerializer

    def get_queryset(self):
        group = self.get_group()
        queryset = (
            Lesson.objects.filter(
                group=group,
                subject_id__in=accessible_subject_ids(self.request.user, group),
            )
            .select_related("group", "subject")
            .prefetch_related("topic_links__topic")
        )
        filters = LessonFilterSerializer(data=self.request.query_params)
        filters.is_valid(raise_exception=True)
        if date_from := filters.validated_data.get("date_from"):
            queryset = queryset.filter(date__gte=date_from)
        if date_to := filters.validated_data.get("date_to"):
            queryset = queryset.filter(date__lte=date_to)
        if subject_id := filters.validated_data.get("subject_id"):
            queryset = queryset.filter(subject_id=subject_id)
        return queryset

    def get_serializer_context(self):
        if getattr(self, "swagger_fake_view", False):
            return super().get_serializer_context()
        return {**super().get_serializer_context(), "group": self.get_group()}


@extend_schema_view(
    get=extend_schema(
        operation_id="journal_lesson_retrieve",
        summary="Read a lesson",
        tags=["Journal"],
        responses={200: LessonSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
    patch=extend_schema(
        operation_id="journal_lesson_update",
        summary="Update a lesson and its ordered topics",
        tags=["Journal"],
        responses={200: LessonSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
    delete=extend_schema(
        operation_id="journal_lesson_delete",
        summary="Delete a lesson",
        tags=["Journal"],
        responses={204: None, **JOURNAL_ERROR_RESPONSES},
    ),
)
class LessonDetailView(JournalGroupContextMixin, generics.RetrieveUpdateDestroyAPIView):
    queryset = Lesson.objects.all()
    serializer_class = LessonSerializer
    lookup_url_kwarg = "lesson_id"
    http_method_names = ("get", "patch", "delete", "head", "options")

    def get_queryset(self):
        return (
            Lesson.objects.filter(group=self.get_group())
            .select_related("group", "subject")
            .prefetch_related("topic_links__topic")
        )

    def get_serializer_context(self):
        if getattr(self, "swagger_fake_view", False):
            return super().get_serializer_context()
        return {**super().get_serializer_context(), "group": self.get_group()}


@extend_schema_view(
    get=extend_schema(
        operation_id="journal_lesson_state_list",
        summary="List student states for a lesson",
        tags=["Journal"],
        responses={200: StudentLessonStateSerializer(many=True), **JOURNAL_ERROR_RESPONSES},
    ),
    post=extend_schema(
        operation_id="journal_lesson_state_create",
        summary="Create a student state for a lesson",
        tags=["Journal"],
        responses={201: StudentLessonStateSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
)
class StudentLessonStateListCreateView(
    JournalGroupContextMixin,
    generics.ListCreateAPIView,
):
    queryset = StudentLessonState.objects.all()
    serializer_class = StudentLessonStateSerializer

    def get_queryset(self):
        return StudentLessonState.objects.filter(lesson=self.get_lesson()).select_related(
            "lesson__subject",
            "student",
        )

    def get_serializer_context(self):
        if getattr(self, "swagger_fake_view", False):
            return super().get_serializer_context()
        return {**super().get_serializer_context(), "lesson": self.get_lesson()}


@extend_schema_view(
    get=extend_schema(
        operation_id="journal_lesson_state_retrieve",
        summary="Read a student state",
        tags=["Journal"],
        responses={200: StudentLessonStateSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
    patch=extend_schema(
        operation_id="journal_lesson_state_update",
        summary="Update attendance, grade or notes",
        tags=["Journal"],
        responses={200: StudentLessonStateSerializer, **JOURNAL_ERROR_RESPONSES},
    ),
    delete=extend_schema(
        operation_id="journal_lesson_state_delete",
        summary="Delete a student state",
        tags=["Journal"],
        responses={204: None, **JOURNAL_ERROR_RESPONSES},
    ),
)
class StudentLessonStateDetailView(
    JournalGroupContextMixin,
    generics.RetrieveUpdateDestroyAPIView,
):
    queryset = StudentLessonState.objects.all()
    serializer_class = StudentLessonStateSerializer
    lookup_url_kwarg = "state_id"
    http_method_names = ("get", "patch", "delete", "head", "options")

    def get_queryset(self):
        return StudentLessonState.objects.filter(lesson=self.get_lesson()).select_related(
            "lesson__subject",
            "student",
        )

    def get_serializer_context(self):
        if getattr(self, "swagger_fake_view", False):
            return super().get_serializer_context()
        return {**super().get_serializer_context(), "lesson": self.get_lesson()}
