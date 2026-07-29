from typing import Any

from django.core.exceptions import ValidationError
from django.db import transaction

from apps.schools.models import Membership, School


class LastAdministratorError(ValidationError):
    pass


def _would_remove_active_admin(
    membership: Membership,
    *,
    role: str,
    is_active: bool,
) -> bool:
    return (
        membership.role == Membership.Role.ADMIN
        and membership.is_active
        and (role != Membership.Role.ADMIN or not is_active)
    )


def _ensure_another_active_admin(membership: Membership) -> None:
    another_admin_exists = (
        Membership.objects.filter(
            school=membership.school,
            role=Membership.Role.ADMIN,
            is_active=True,
            user__is_active=True,
        )
        .exclude(pk=membership.pk)
        .exists()
    )
    if not another_admin_exists:
        raise LastAdministratorError("A school must retain at least one active administrator.")


@transaction.atomic
def create_membership(
    *,
    user: Any,
    school: School,
    role: str,
    is_active: bool = True,
) -> Membership:
    School.objects.select_for_update().get(pk=school.pk)
    membership = Membership(
        user=user,
        school=school,
        role=role,
        is_active=is_active,
    )
    membership.full_clean()
    membership.save()
    return membership


@transaction.atomic
def update_membership(
    membership_id: Any,
    *,
    role: str,
    is_active: bool,
) -> Membership:
    membership = Membership.objects.select_related("school").get(pk=membership_id)
    School.objects.select_for_update().get(pk=membership.school_id)
    membership = Membership.objects.select_for_update().get(pk=membership_id)
    if _would_remove_active_admin(
        membership,
        role=role,
        is_active=is_active,
    ):
        _ensure_another_active_admin(membership)
    membership.role = role
    membership.is_active = is_active
    membership.full_clean()
    membership.save(update_fields=["role", "is_active", "updated_at"])
    return membership


@transaction.atomic
def delete_membership(membership_id: Any) -> None:
    membership = Membership.objects.select_related("school").get(pk=membership_id)
    School.objects.select_for_update().get(pk=membership.school_id)
    membership = Membership.objects.select_for_update().get(pk=membership_id)
    if membership.role == Membership.Role.ADMIN and membership.is_active:
        _ensure_another_active_admin(membership)
    membership.delete()
