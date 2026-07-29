from datetime import date

import pytest
from django.db import IntegrityError, transaction

from apps.curriculum.models import (
    Subject,
    Topic,
    TopicCriterion,
    TopicGroupAssignment,
    TopicPeriodAssignment,
)
from apps.education.models import AcademicPeriod, AcademicYear, Group
from apps.schools.models import School


@pytest.fixture
def curriculum_context() -> tuple[Subject, Topic, Group, AcademicPeriod]:
    school = School.objects.create(
        name="Art school",
        slug="art-school",
        default_currency="RUB",
    )
    academic_year = AcademicYear.objects.create(
        school=school,
        name="2026-2027",
        is_active=True,
    )
    group = Group.objects.create(
        academic_year=academic_year,
        name="Painting group",
    )
    period = AcademicPeriod.objects.create(
        academic_year=academic_year,
        name="First quarter",
        start_date=date(2026, 9, 1),
        end_date=date(2026, 10, 25),
    )
    subject = Subject.objects.create(
        school=school,
        name="Painting",
    )
    topic = Topic.objects.create(
        subject=subject,
        name="Still life",
    )
    return subject, topic, group, period


@pytest.mark.django_db
def test_topic_criteria_have_unique_names_and_positions(
    curriculum_context: tuple[Subject, Topic, Group, AcademicPeriod],
) -> None:
    _, topic, _, _ = curriculum_context
    TopicCriterion.objects.create(
        topic=topic,
        name="Composition",
        max_points=10,
        position=0,
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            TopicCriterion.objects.create(
                topic=topic,
                name="Colour",
                max_points=10,
                position=0,
            )


@pytest.mark.django_db
def test_topic_criterion_requires_positive_max_points(
    curriculum_context: tuple[Subject, Topic, Group, AcademicPeriod],
) -> None:
    _, topic, _, _ = curriculum_context

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            TopicCriterion.objects.create(
                topic=topic,
                name="Composition",
                max_points=0,
                position=0,
            )


@pytest.mark.django_db
def test_topic_group_and_period_assignments_are_independent(
    curriculum_context: tuple[Subject, Topic, Group, AcademicPeriod],
) -> None:
    _, topic, group, period = curriculum_context

    group_assignment = TopicGroupAssignment.objects.create(
        topic=topic,
        group=group,
    )
    period_assignment = TopicPeriodAssignment.objects.create(
        topic=topic,
        academic_period=period,
    )

    assert group_assignment.group == group
    assert period_assignment.academic_period == period
