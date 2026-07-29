import pytest
from django.core.exceptions import ValidationError
from django.db import IntegrityError, transaction
from django.db.models.deletion import ProtectedError

from apps.accounts.models import User
from apps.curriculum.models import Subject
from apps.education.models import AcademicYear, Group
from apps.schools.access import (
    GroupAction,
    SchoolAction,
    accessible_group_ids,
    has_group_action,
    has_school_action,
)
from apps.schools.models import Membership, School, TeachingAssignment
from apps.schools.services import (
    LastAdministratorError,
    delete_membership,
    update_membership,
)


@pytest.mark.django_db
def test_user_has_only_one_membership_per_school(
    teacher_user: User,
    school: School,
    teacher_membership: Membership,
) -> None:
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            Membership.objects.create(
                user=teacher_user,
                school=school,
                role=Membership.Role.ADMIN,
            )


@pytest.mark.django_db
def test_membership_prevents_deleting_linked_user(
    teacher_user: User,
    teacher_membership: Membership,
) -> None:
    with pytest.raises(ProtectedError):
        teacher_user.delete()


@pytest.mark.django_db
def test_teaching_assignment_requires_teacher(
    admin_membership: Membership,
    group: Group,
) -> None:
    assignment = TeachingAssignment(
        membership=admin_membership,
        group=group,
    )

    with pytest.raises(ValidationError, match="teacher membership"):
        assignment.full_clean()


@pytest.mark.django_db
def test_teaching_assignment_requires_group_subject(
    teacher_membership: Membership,
    group: Group,
    school: School,
) -> None:
    unassigned_subject = Subject.objects.create(
        school=school,
        name="Sculpture",
    )
    assignment = TeachingAssignment(
        membership=teacher_membership,
        group=group,
        subject=unassigned_subject,
    )

    with pytest.raises(ValidationError, match="assigned to the selected group"):
        assignment.full_clean()


@pytest.mark.django_db
def test_teaching_assignment_stays_inside_school(
    teacher_membership: Membership,
    other_school: School,
) -> None:
    other_year = AcademicYear.objects.create(
        school=other_school,
        name="2026/2027",
    )
    other_group = Group.objects.create(
        academic_year=other_year,
        name="Other Group",
    )
    assignment = TeachingAssignment(
        membership=teacher_membership,
        group=other_group,
    )

    with pytest.raises(ValidationError, match="same school"):
        assignment.full_clean()


@pytest.mark.django_db
def test_group_wide_and_subject_assignments_cannot_overlap(
    teacher_membership: Membership,
    group: Group,
    subjects: tuple[Subject, Subject],
) -> None:
    painting, _ = subjects
    TeachingAssignment.objects.create(
        membership=teacher_membership,
        group=group,
        subject=painting,
    )
    broad_assignment = TeachingAssignment(
        membership=teacher_membership,
        group=group,
        subject=None,
    )

    with pytest.raises(ValidationError, match="cannot be combined"):
        broad_assignment.full_clean()


@pytest.mark.django_db
def test_teacher_with_assignments_cannot_be_promoted(
    teacher_membership: Membership,
    group: Group,
) -> None:
    TeachingAssignment.objects.create(
        membership=teacher_membership,
        group=group,
    )

    with pytest.raises(ValidationError, match="Remove teaching assignments"):
        update_membership(
            teacher_membership.pk,
            role=Membership.Role.ADMIN,
            is_active=True,
        )


@pytest.mark.django_db
def test_cannot_demote_or_delete_last_active_administrator(
    admin_membership: Membership,
) -> None:
    with pytest.raises(LastAdministratorError):
        update_membership(
            admin_membership.pk,
            role=Membership.Role.TEACHER,
            is_active=True,
        )
    with pytest.raises(LastAdministratorError):
        delete_membership(admin_membership.pk)

    admin_membership.refresh_from_db()
    assert admin_membership.role == Membership.Role.ADMIN
    assert admin_membership.is_active is True


@pytest.mark.django_db
def test_inactive_second_administrator_does_not_satisfy_invariant(
    admin_membership: Membership,
    school: School,
) -> None:
    inactive_user = User.objects.create_user(
        username="inactive-admin",
        is_active=False,
    )
    Membership.objects.create(
        user=inactive_user,
        school=school,
        role=Membership.Role.ADMIN,
    )

    with pytest.raises(LastAdministratorError):
        delete_membership(admin_membership.pk)


@pytest.mark.django_db
def test_administrator_can_manage_school_and_all_groups(
    admin_user: User,
    admin_membership: Membership,
    school: School,
    group: Group,
) -> None:
    assert all(has_school_action(admin_user, school, action) for action in SchoolAction)
    assert all(has_group_action(admin_user, group, action) for action in GroupAction)
    assert accessible_group_ids(admin_user, school) == {group.id}


@pytest.mark.django_db
def test_teacher_access_is_limited_to_assignment_subject(
    teacher_user: User,
    teacher_membership: Membership,
    school: School,
    group: Group,
    subjects: tuple[Subject, Subject],
) -> None:
    painting, drawing = subjects
    TeachingAssignment.objects.create(
        membership=teacher_membership,
        group=group,
        subject=painting,
    )

    assert has_school_action(teacher_user, school, SchoolAction.VIEW_SCHOOL)
    assert not has_school_action(teacher_user, school, SchoolAction.MANAGE_MEMBERS)
    assert has_group_action(
        teacher_user,
        group,
        GroupAction.EDIT_JOURNAL,
        subject=painting,
    )
    assert not has_group_action(
        teacher_user,
        group,
        GroupAction.EDIT_JOURNAL,
        subject=drawing,
    )
    assert accessible_group_ids(teacher_user, school) == {group.id}


@pytest.mark.django_db
def test_group_wide_assignment_grants_all_subjects(
    teacher_user: User,
    teacher_membership: Membership,
    group: Group,
    subjects: tuple[Subject, Subject],
) -> None:
    TeachingAssignment.objects.create(
        membership=teacher_membership,
        group=group,
        subject=None,
    )

    assert all(
        has_group_action(
            teacher_user,
            group,
            GroupAction.EDIT_JOURNAL,
            subject=subject,
        )
        for subject in subjects
    )


@pytest.mark.django_db
def test_inactive_membership_denies_access(
    teacher_user: User,
    teacher_membership: Membership,
    school: School,
) -> None:
    teacher_membership.is_active = False
    teacher_membership.save(update_fields=["is_active"])

    assert not has_school_action(teacher_user, school, SchoolAction.VIEW_SCHOOL)
    assert accessible_group_ids(teacher_user, school) == set()
