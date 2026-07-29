from enum import StrEnum
from typing import Any

from django.db.models import Q

from apps.schools.models import Membership, School, TeachingAssignment


class SchoolAction(StrEnum):
    VIEW_SCHOOL = "view_school"
    MANAGE_SCHOOL = "manage_school"
    MANAGE_MEMBERS = "manage_members"
    MANAGE_CURRICULUM = "manage_curriculum"
    IMPORT_DATA = "import_data"
    VIEW_TUITION = "view_tuition"
    MANAGE_TUITION = "manage_tuition"


class GroupAction(StrEnum):
    VIEW_GROUP = "view_group"
    EDIT_JOURNAL = "edit_journal"


ADMIN_SCHOOL_ACTIONS = frozenset(SchoolAction)
TEACHER_SCHOOL_ACTIONS = frozenset({SchoolAction.VIEW_SCHOOL})
TEACHER_GROUP_ACTIONS = frozenset(GroupAction)


def _is_authenticated_active(user: Any) -> bool:
    return bool(getattr(user, "is_authenticated", False) and getattr(user, "is_active", False))


def active_membership(user: Any, school: School) -> Membership | None:
    if not _is_authenticated_active(user):
        return None
    return (
        Membership.objects.filter(
            user=user,
            school=school,
            is_active=True,
        )
        .select_related("school", "user")
        .first()
    )


def has_school_action(
    user: Any,
    school: School,
    action: SchoolAction,
) -> bool:
    if not _is_authenticated_active(user):
        return False
    if getattr(user, "is_superuser", False):
        return True
    membership = active_membership(user, school)
    if membership is None:
        return False
    if membership.role == Membership.Role.ADMIN:
        return action in ADMIN_SCHOOL_ACTIONS
    if membership.role == Membership.Role.TEACHER:
        return action in TEACHER_SCHOOL_ACTIONS
    return False


def has_group_action(
    user: Any,
    group: Any,
    action: GroupAction,
    *,
    subject: Any | None = None,
) -> bool:
    if not _is_authenticated_active(user):
        return False
    if getattr(user, "is_superuser", False):
        return True
    school = group.academic_year.school
    membership = active_membership(user, school)
    if membership is None:
        return False
    if membership.role == Membership.Role.ADMIN:
        return True
    if membership.role != Membership.Role.TEACHER:
        return False
    if action not in TEACHER_GROUP_ACTIONS:
        return False

    assignments = TeachingAssignment.objects.filter(
        membership=membership,
        group=group,
    )
    if subject is None:
        return assignments.exists()
    return assignments.filter(Q(subject__isnull=True) | Q(subject=subject)).exists()


def accessible_group_ids(user: Any, school: School) -> set[Any]:
    if not _is_authenticated_active(user):
        return set()
    if getattr(user, "is_superuser", False):
        return set(
            school.academic_years.filter(groups__id__isnull=False).values_list(
                "groups__id",
                flat=True,
            )
        )
    membership = active_membership(user, school)
    if membership is None:
        return set()
    if membership.role == Membership.Role.ADMIN:
        return set(
            school.academic_years.filter(groups__id__isnull=False).values_list(
                "groups__id",
                flat=True,
            )
        )
    if membership.role == Membership.Role.TEACHER:
        return set(membership.teaching_assignments.values_list("group_id", flat=True))
    return set()
