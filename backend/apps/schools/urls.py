from django.urls import path

from apps.schools.views import (
    MembershipDetailView,
    MembershipListCreateView,
    SchoolDetailView,
    SchoolListView,
    TeachingAssignmentDetailView,
    TeachingAssignmentListCreateView,
)

urlpatterns = [
    path("", SchoolListView.as_view(), name="school-list"),
    path("<uuid:school_id>/", SchoolDetailView.as_view(), name="school-detail"),
    path(
        "<uuid:school_id>/memberships/",
        MembershipListCreateView.as_view(),
        name="membership-list",
    ),
    path(
        "<uuid:school_id>/memberships/<uuid:membership_id>/",
        MembershipDetailView.as_view(),
        name="membership-detail",
    ),
    path(
        "<uuid:school_id>/teaching-assignments/",
        TeachingAssignmentListCreateView.as_view(),
        name="teaching-assignment-list",
    ),
    path(
        "<uuid:school_id>/teaching-assignments/<uuid:assignment_id>/",
        TeachingAssignmentDetailView.as_view(),
        name="teaching-assignment-detail",
    ),
]
