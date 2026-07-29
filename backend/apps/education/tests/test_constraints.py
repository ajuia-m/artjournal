from datetime import date

import pytest
from django.core.exceptions import ValidationError
from django.db import IntegrityError, transaction

from apps.education.models import (
    AcademicPeriod,
    AcademicYear,
    CalendarException,
    Enrollment,
    Group,
    Student,
)
from apps.schools.models import School


@pytest.fixture
def school() -> School:
    return School.objects.create(
        name="Art school",
        slug="art-school",
        default_currency="RUB",
    )


@pytest.fixture
def academic_year(school: School) -> AcademicYear:
    return AcademicYear.objects.create(
        school=school,
        name="2026-2027",
        is_active=True,
    )


@pytest.mark.django_db
def test_only_one_academic_year_can_be_active_per_school(
    school: School,
    academic_year: AcademicYear,
) -> None:
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            AcademicYear.objects.create(
                school=school,
                name="2027-2028",
                is_active=True,
            )


@pytest.mark.django_db
def test_academic_period_rejects_inverted_dates(
    academic_year: AcademicYear,
) -> None:
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            AcademicPeriod.objects.create(
                academic_year=academic_year,
                name="Broken period",
                start_date=date(2026, 10, 1),
                end_date=date(2026, 9, 1),
            )


@pytest.mark.django_db
def test_student_has_at_most_one_current_enrollment(
    school: School,
    academic_year: AcademicYear,
) -> None:
    first_group = Group.objects.create(
        academic_year=academic_year,
        name="First group",
    )
    second_group = Group.objects.create(
        academic_year=academic_year,
        name="Second group",
    )
    student = Student.objects.create(
        school=school,
        last_name="Example",
        first_name="Student",
    )
    Enrollment.objects.create(
        student=student,
        group=first_group,
        started_on=date(2026, 9, 1),
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            Enrollment.objects.create(
                student=student,
                group=second_group,
                started_on=date(2026, 10, 1),
            )


@pytest.mark.django_db
def test_enrollment_rejects_group_from_another_school(
    school: School,
    academic_year: AcademicYear,
) -> None:
    other_school = School.objects.create(
        name="Other school",
        slug="other-school",
        default_currency="RUB",
    )
    other_year = AcademicYear.objects.create(
        school=other_school,
        name="2026-2027",
    )
    group = Group.objects.create(
        academic_year=other_year,
        name="Other group",
    )
    student = Student.objects.create(
        school=school,
        last_name="Example",
        first_name="Student",
    )
    enrollment = Enrollment(
        student=student,
        group=group,
        started_on=date(2026, 9, 1),
    )

    with pytest.raises(ValidationError):
        enrollment.full_clean()


@pytest.mark.django_db
def test_calendar_exception_is_unique_within_its_scope(
    academic_year: AcademicYear,
) -> None:
    CalendarException.objects.create(
        academic_year=academic_year,
        date=date(2027, 1, 1),
        kind=CalendarException.Kind.HOLIDAY,
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            CalendarException.objects.create(
                academic_year=academic_year,
                date=date(2027, 1, 1),
                kind=CalendarException.Kind.HOLIDAY,
            )
