import pytest
from rest_framework.test import APIClient

from apps.accounts.models import User
from apps.curriculum.models import Subject
from apps.education.models import AcademicYear, Group
from apps.schools.models import Membership, School, TeachingAssignment


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.mark.django_db
def test_school_endpoints_require_authentication(
    api_client: APIClient,
    school: School,
) -> None:
    assert api_client.get("/api/v1/schools/").status_code == 401
    assert api_client.get(f"/api/v1/schools/{school.id}/").status_code == 401


@pytest.mark.django_db
def test_teacher_lists_only_active_school_memberships(
    api_client: APIClient,
    teacher_user: User,
    teacher_membership: Membership,
    school: School,
    other_school: School,
) -> None:
    Membership.objects.create(
        user=teacher_user,
        school=other_school,
        role=Membership.Role.TEACHER,
        is_active=False,
    )
    api_client.force_authenticate(teacher_user)

    response = api_client.get("/api/v1/schools/")

    assert response.status_code == 200
    assert [item["id"] for item in response.json()] == [str(school.id)]


@pytest.mark.django_db
def test_teacher_cannot_manage_memberships(
    api_client: APIClient,
    teacher_user: User,
    teacher_membership: Membership,
    school: School,
) -> None:
    api_client.force_authenticate(teacher_user)

    response = api_client.get(f"/api/v1/schools/{school.id}/memberships/")

    assert response.status_code == 403


@pytest.mark.django_db
def test_administrator_cannot_manage_another_school(
    api_client: APIClient,
    admin_user: User,
    admin_membership: Membership,
    other_school: School,
) -> None:
    api_client.force_authenticate(admin_user)

    response = api_client.get(f"/api/v1/schools/{other_school.id}/memberships/")

    assert response.status_code == 403


@pytest.mark.django_db
def test_administrator_creates_teacher_membership(
    api_client: APIClient,
    admin_user: User,
    admin_membership: Membership,
    school: School,
    teacher_user: User,
) -> None:
    api_client.force_authenticate(admin_user)

    response = api_client.post(
        f"/api/v1/schools/{school.id}/memberships/",
        {
            "user_id": str(teacher_user.id),
            "role": Membership.Role.TEACHER,
            "is_active": True,
        },
        format="json",
    )

    assert response.status_code == 201
    assert Membership.objects.filter(
        user=teacher_user,
        school=school,
        role=Membership.Role.TEACHER,
    ).exists()


@pytest.mark.django_db
def test_administrator_cannot_delete_last_administrator(
    api_client: APIClient,
    admin_user: User,
    admin_membership: Membership,
    school: School,
) -> None:
    api_client.force_authenticate(admin_user)

    response = api_client.delete(f"/api/v1/schools/{school.id}/memberships/{admin_membership.id}/")

    assert response.status_code == 400
    assert Membership.objects.filter(pk=admin_membership.pk).exists()


@pytest.mark.django_db
def test_administrator_creates_teacher_assignment(
    api_client: APIClient,
    admin_user: User,
    admin_membership: Membership,
    teacher_membership: Membership,
    school: School,
    group: Group,
    subjects: tuple[Subject, Subject],
) -> None:
    painting, _ = subjects
    api_client.force_authenticate(admin_user)

    response = api_client.post(
        f"/api/v1/schools/{school.id}/teaching-assignments/",
        {
            "membership_id": str(teacher_membership.id),
            "group_id": str(group.id),
            "subject_id": str(painting.id),
        },
        format="json",
    )

    assert response.status_code == 201
    assert TeachingAssignment.objects.filter(
        membership=teacher_membership,
        group=group,
        subject=painting,
    ).exists()


@pytest.mark.django_db
def test_assignment_rejects_group_from_another_school(
    api_client: APIClient,
    admin_user: User,
    admin_membership: Membership,
    teacher_membership: Membership,
    school: School,
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
    api_client.force_authenticate(admin_user)

    response = api_client.post(
        f"/api/v1/schools/{school.id}/teaching-assignments/",
        {
            "membership_id": str(teacher_membership.id),
            "group_id": str(other_group.id),
            "subject_id": None,
        },
        format="json",
    )

    assert response.status_code == 400
    assert not TeachingAssignment.objects.exists()
