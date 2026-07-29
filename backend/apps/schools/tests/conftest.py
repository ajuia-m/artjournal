import pytest

from apps.accounts.models import User
from apps.curriculum.models import GroupSubject, Subject
from apps.education.models import AcademicYear, Group
from apps.schools.models import Membership, School


@pytest.fixture
def school() -> School:
    return School.objects.create(
        name="Central Art School",
        slug="central-art-school",
        default_currency="RUB",
    )


@pytest.fixture
def other_school() -> School:
    return School.objects.create(
        name="Other Art School",
        slug="other-art-school",
        default_currency="RUB",
    )


@pytest.fixture
def admin_user() -> User:
    return User.objects.create_user(
        username="school-admin",
        password="strong-test-password",
    )


@pytest.fixture
def teacher_user() -> User:
    return User.objects.create_user(
        username="teacher",
        password="strong-test-password",
    )


@pytest.fixture
def admin_membership(admin_user: User, school: School) -> Membership:
    return Membership.objects.create(
        user=admin_user,
        school=school,
        role=Membership.Role.ADMIN,
    )


@pytest.fixture
def teacher_membership(teacher_user: User, school: School) -> Membership:
    return Membership.objects.create(
        user=teacher_user,
        school=school,
        role=Membership.Role.TEACHER,
    )


@pytest.fixture
def academic_year(school: School) -> AcademicYear:
    return AcademicYear.objects.create(
        school=school,
        name="2026/2027",
        is_active=True,
    )


@pytest.fixture
def group(academic_year: AcademicYear) -> Group:
    return Group.objects.create(
        academic_year=academic_year,
        name="Group A",
    )


@pytest.fixture
def subjects(school: School, group: Group) -> tuple[Subject, Subject]:
    painting = Subject.objects.create(school=school, name="Painting")
    drawing = Subject.objects.create(school=school, name="Drawing")
    GroupSubject.objects.create(group=group, subject=painting)
    GroupSubject.objects.create(group=group, subject=drawing)
    return painting, drawing
