from django.shortcuts import get_object_or_404
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


class SchoolDetailView(SchoolContextMixin, generics.RetrieveAPIView):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    serializer_class = SchoolSerializer
    lookup_url_kwarg = "school_id"
    queryset = School.objects.all()


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
