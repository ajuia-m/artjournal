from django.shortcuts import get_object_or_404
from drf_spectacular.utils import extend_schema, extend_schema_view
from rest_framework import generics
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import IsAuthenticated

from apps.schools.access import SchoolAction
from apps.schools.models import Membership, School, TeachingAssignment
from apps.schools.permissions import HasSchoolAction
from apps.schools.serializers import (
    MembershipSerializer,
    SchoolSerializer,
    TeachingAssignmentSerializer,
)
from apps.schools.services import LastAdministratorError, delete_membership


class SchoolContextMixin:
    required_school_action = SchoolAction.VIEW_SCHOOL

    def get_permission_school(self, request, obj=None):
        return get_object_or_404(School, pk=self.kwargs["school_id"])

    def get_school(self) -> School:
        if not hasattr(self, "_school"):
            self._school = get_object_or_404(School, pk=self.kwargs["school_id"])
        return self._school


@extend_schema_view(
    get=extend_schema(
        operation_id="school_list",
        summary="List schools available to the current user",
        tags=["Schools"],
    )
)
class SchoolListView(generics.ListAPIView):
    permission_classes = (IsAuthenticated,)
    serializer_class = SchoolSerializer

    def get_queryset(self):
        if not self.request.user.is_active:
            return School.objects.none()
        if self.request.user.is_superuser:
            return School.objects.all()
        return School.objects.filter(
            memberships__user=self.request.user,
            memberships__is_active=True,
        ).distinct()


@extend_schema_view(
    get=extend_schema(
        operation_id="school_retrieve",
        summary="Read an available school",
        tags=["Schools"],
    )
)
class SchoolDetailView(SchoolContextMixin, generics.RetrieveAPIView):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    serializer_class = SchoolSerializer
    lookup_url_kwarg = "school_id"
    queryset = School.objects.all()


@extend_schema_view(
    get=extend_schema(
        operation_id="membership_list",
        summary="List school memberships",
        tags=["Memberships"],
    ),
    post=extend_schema(
        operation_id="membership_create",
        summary="Create a school membership",
        tags=["Memberships"],
    ),
)
class MembershipListCreateView(SchoolContextMixin, generics.ListCreateAPIView):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.MANAGE_MEMBERS
    serializer_class = MembershipSerializer

    def get_queryset(self):
        return Membership.objects.filter(school=self.get_school()).select_related(
            "school",
            "user",
        )

    def get_serializer_context(self):
        return {**super().get_serializer_context(), "school": self.get_school()}


@extend_schema_view(
    get=extend_schema(
        operation_id="membership_retrieve",
        summary="Read a school membership",
        tags=["Memberships"],
    ),
    patch=extend_schema(
        operation_id="membership_update",
        summary="Change a membership role or active state",
        tags=["Memberships"],
    ),
    delete=extend_schema(
        operation_id="membership_delete",
        summary="Delete a school membership",
        tags=["Memberships"],
    ),
)
class MembershipDetailView(
    SchoolContextMixin,
    generics.RetrieveUpdateDestroyAPIView,
):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.MANAGE_MEMBERS
    serializer_class = MembershipSerializer
    lookup_url_kwarg = "membership_id"
    http_method_names = ("get", "patch", "delete", "head", "options")

    def get_queryset(self):
        return Membership.objects.filter(school=self.get_school()).select_related(
            "school",
            "user",
        )

    def get_serializer_context(self):
        return {**super().get_serializer_context(), "school": self.get_school()}

    def perform_destroy(self, instance):
        try:
            delete_membership(instance.pk)
        except LastAdministratorError as error:
            raise ValidationError({"detail": error.messages}) from error


@extend_schema_view(
    get=extend_schema(
        operation_id="teaching_assignment_list",
        summary="List teaching assignments",
        tags=["Teaching assignments"],
    ),
    post=extend_schema(
        operation_id="teaching_assignment_create",
        summary="Create a teaching assignment",
        tags=["Teaching assignments"],
    ),
)
class TeachingAssignmentListCreateView(
    SchoolContextMixin,
    generics.ListCreateAPIView,
):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.MANAGE_MEMBERS
    serializer_class = TeachingAssignmentSerializer

    def get_queryset(self):
        return TeachingAssignment.objects.filter(
            membership__school=self.get_school()
        ).select_related(
            "membership__user",
            "group",
            "subject",
        )

    def get_serializer_context(self):
        return {**super().get_serializer_context(), "school": self.get_school()}


@extend_schema_view(
    get=extend_schema(
        operation_id="teaching_assignment_retrieve",
        summary="Read a teaching assignment",
        tags=["Teaching assignments"],
    ),
    put=extend_schema(
        operation_id="teaching_assignment_replace",
        summary="Replace a teaching assignment",
        tags=["Teaching assignments"],
    ),
    patch=extend_schema(
        operation_id="teaching_assignment_update",
        summary="Update a teaching assignment",
        tags=["Teaching assignments"],
    ),
    delete=extend_schema(
        operation_id="teaching_assignment_delete",
        summary="Delete a teaching assignment",
        tags=["Teaching assignments"],
    ),
)
class TeachingAssignmentDetailView(
    SchoolContextMixin,
    generics.RetrieveUpdateDestroyAPIView,
):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.MANAGE_MEMBERS
    serializer_class = TeachingAssignmentSerializer
    lookup_url_kwarg = "assignment_id"

    def get_queryset(self):
        return TeachingAssignment.objects.filter(
            membership__school=self.get_school()
        ).select_related(
            "membership__user",
            "group",
            "subject",
        )

    def get_serializer_context(self):
        return {**super().get_serializer_context(), "school": self.get_school()}
