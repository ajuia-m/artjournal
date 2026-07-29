from datetime import date

import pytest
from django.core.exceptions import ValidationError
from django.db import IntegrityError, transaction

from apps.curriculum.models import Subject, Topic, TopicCriterion
from apps.education.models import AcademicYear, Group, Student
from apps.journal.models import (
    CriterionScore,
    Lesson,
    LessonTopic,
    StudentLessonState,
    StudentTopicProgress,
)
from apps.schools.models import School


@pytest.fixture
def journal_context() -> dict[str, object]:
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
    student = Student.objects.create(
        school=school,
        last_name="Example",
        first_name="Student",
    )
    subject = Subject.objects.create(
        school=school,
        name="Painting",
    )
    first_topic = Topic.objects.create(
        subject=subject,
        name="Still life",
    )
    second_topic = Topic.objects.create(
        subject=subject,
        name="Colour study",
    )
    first_lesson = Lesson.objects.create(
        group=group,
        subject=subject,
        date=date(2026, 9, 7),
    )
    second_lesson = Lesson.objects.create(
        group=group,
        subject=subject,
        date=date(2026, 9, 14),
    )
    return {
        "student": student,
        "first_topic": first_topic,
        "second_topic": second_topic,
        "first_lesson": first_lesson,
        "second_lesson": second_lesson,
    }


@pytest.mark.django_db
def test_topics_and_lessons_form_many_to_many_relationship(
    journal_context: dict[str, object],
) -> None:
    first_topic = journal_context["first_topic"]
    second_topic = journal_context["second_topic"]
    first_lesson = journal_context["first_lesson"]
    second_lesson = journal_context["second_lesson"]

    LessonTopic.objects.create(
        lesson=first_lesson,
        topic=first_topic,
        position=0,
    )
    LessonTopic.objects.create(
        lesson=first_lesson,
        topic=second_topic,
        position=1,
    )
    LessonTopic.objects.create(
        lesson=second_lesson,
        topic=first_topic,
        position=0,
    )

    assert first_lesson.topic_links.count() == 2
    assert first_topic.lesson_links.count() == 2


@pytest.mark.django_db
def test_lesson_topic_pair_is_unique(
    journal_context: dict[str, object],
) -> None:
    topic = journal_context["first_topic"]
    lesson = journal_context["first_lesson"]
    LessonTopic.objects.create(lesson=lesson, topic=topic, position=0)

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            LessonTopic.objects.create(lesson=lesson, topic=topic, position=1)


@pytest.mark.django_db
def test_lesson_rejects_subject_from_another_school(
    journal_context: dict[str, object],
) -> None:
    other_school = School.objects.create(
        name="Other school",
        slug="other-school",
        default_currency="RUB",
    )
    other_subject = Subject.objects.create(
        school=other_school,
        name="Sculpture",
    )
    lesson = Lesson(
        group=journal_context["first_lesson"].group,
        subject=other_subject,
        date=date(2026, 9, 21),
    )

    with pytest.raises(ValidationError):
        lesson.full_clean()


@pytest.mark.django_db
def test_student_lesson_state_rejects_invalid_grade(
    journal_context: dict[str, object],
) -> None:
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            StudentLessonState.objects.create(
                student=journal_context["student"],
                lesson=journal_context["first_lesson"],
                grade=6,
            )


@pytest.mark.django_db
def test_student_topic_progress_is_unique(
    journal_context: dict[str, object],
) -> None:
    student = journal_context["student"]
    topic = journal_context["first_topic"]
    StudentTopicProgress.objects.create(
        student=student,
        topic=topic,
        stage=50,
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            StudentTopicProgress.objects.create(
                student=student,
                topic=topic,
                stage=75,
            )


@pytest.mark.django_db
def test_criterion_score_cannot_exceed_maximum(
    journal_context: dict[str, object],
) -> None:
    topic = journal_context["first_topic"]
    progress = StudentTopicProgress.objects.create(
        student=journal_context["student"],
        topic=topic,
        stage=50,
    )
    criterion = TopicCriterion.objects.create(
        topic=topic,
        name="Composition",
        max_points=10,
        position=0,
    )
    score = CriterionScore(
        progress=progress,
        criterion=criterion,
        score=11,
    )

    with pytest.raises(ValidationError):
        score.full_clean()
