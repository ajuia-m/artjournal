from types import SimpleNamespace

import pytest
from rest_framework.test import APIClient

from apps.accounts.models import User
from apps.curriculum.models import GroupSubject, Subject
from apps.education.models import AcademicYear, Enrollment, Group, Student
from apps.journal.models import Lesson
from apps.schools.models import Membership, School, TeachingAssignment


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.fixture
def sync_data() -> SimpleNamespace:
    school = School.objects.create(
        name="Central Art School",
        slug="central-sync-school",
        default_currency="RUB",
    )
    other_school = School.objects.create(
        name="Other Art School",
        slug="other-sync-school",
        default_currency="USD",
    )
    admin = User.objects.create_user(username="sync-admin")
    teacher = User.objects.create_user(username="sync-teacher")
    revoked_teacher = User.objects.create_user(username="revoked-sync-teacher")
    outsider = User.objects.create_user(username="sync-outsider")

    Membership.objects.create(user=admin, school=school, role=Membership.Role.ADMIN)
    teacher_membership = Membership.objects.create(
        user=teacher,
        school=school,
        role=Membership.Role.TEACHER,
    )
    revoked_membership = Membership.objects.create(
        user=revoked_teacher,
        school=school,
        role=Membership.Role.TEACHER,
        is_active=False,
    )
    Membership.objects.create(
        user=outsider,
        school=other_school,
        role=Membership.Role.ADMIN,
    )

    year = AcademicYear.objects.create(school=school, name="2026/2027", is_active=True)
    group = Group.objects.create(academic_year=year, name="Painting 1")
    painting = Subject.objects.create(school=school, name="Painting")
    drawing = Subject.objects.create(school=school, name="Drawing")
    GroupSubject.objects.create(group=group, subject=painting)
    GroupSubject.objects.create(group=group, subject=drawing)
    TeachingAssignment.objects.create(
        membership=teacher_membership,
        group=group,
        subject=painting,
    )
    TeachingAssignment.objects.create(
        membership=revoked_membership,
        group=group,
        subject=painting,
    )

    student = Student.objects.create(
        school=school,
        first_name="Anna",
        last_name="Petrova",
    )
    Enrollment.objects.create(
        student=student,
        group=group,
        started_on="2026-09-01",
    )
    painting_lesson = Lesson.objects.create(
        group=group,
        subject=painting,
        date="2026-09-15",
    )
    drawing_lesson = Lesson.objects.create(
        group=group,
        subject=drawing,
        date="2026-09-16",
    )

    return SimpleNamespace(
        school=school,
        other_school=other_school,
        admin=admin,
        teacher=teacher,
        revoked_teacher=revoked_teacher,
        outsider=outsider,
        group=group,
        painting=painting,
        drawing=drawing,
        student=student,
        painting_lesson=painting_lesson,
        drawing_lesson=drawing_lesson,
    )
