from types import SimpleNamespace

import pytest
from rest_framework.test import APIClient

from apps.accounts.models import User
from apps.curriculum.models import (
    GroupSubject,
    Subject,
    Topic,
    TopicGroupAssignment,
)
from apps.education.models import AcademicYear, Enrollment, Group, Student
from apps.journal.models import Lesson, LessonTopic, StudentLessonState
from apps.schools.models import Membership, School, TeachingAssignment


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.fixture
def journal_data() -> SimpleNamespace:
    school = School.objects.create(
        name="Central Art School",
        slug="central-art-school",
        default_currency="RUB",
    )
    other_school = School.objects.create(
        name="Other Art School",
        slug="other-art-school",
        default_currency="USD",
    )
    admin = User.objects.create_user(username="journal-admin")
    teacher = User.objects.create_user(username="journal-teacher")
    unassigned_teacher = User.objects.create_user(username="unassigned-teacher")
    outsider = User.objects.create_user(username="other-school-admin")
    Membership.objects.create(user=admin, school=school, role=Membership.Role.ADMIN)
    teacher_membership = Membership.objects.create(
        user=teacher,
        school=school,
        role=Membership.Role.TEACHER,
    )
    unassigned_membership = Membership.objects.create(
        user=unassigned_teacher,
        school=school,
        role=Membership.Role.TEACHER,
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
    painting_topic = Topic.objects.create(subject=painting, name="Warm and cool colours")
    second_painting_topic = Topic.objects.create(subject=painting, name="Colour contrast")
    drawing_topic = Topic.objects.create(subject=drawing, name="Perspective")
    TopicGroupAssignment.objects.create(topic=painting_topic, group=group)
    TopicGroupAssignment.objects.create(topic=second_painting_topic, group=group)
    TopicGroupAssignment.objects.create(topic=drawing_topic, group=group)

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
    unenrolled_student = Student.objects.create(
        school=school,
        first_name="Ivan",
        last_name="Sidorov",
    )
    other_school_student = Student.objects.create(
        school=other_school,
        first_name="Maria",
        last_name="Orlova",
    )

    return SimpleNamespace(
        school=school,
        other_school=other_school,
        admin=admin,
        teacher=teacher,
        teacher_membership=teacher_membership,
        unassigned_teacher=unassigned_teacher,
        unassigned_membership=unassigned_membership,
        outsider=outsider,
        group=group,
        painting=painting,
        drawing=drawing,
        painting_topic=painting_topic,
        second_painting_topic=second_painting_topic,
        drawing_topic=drawing_topic,
        student=student,
        unenrolled_student=unenrolled_student,
        other_school_student=other_school_student,
    )


def lesson_list_url(data: SimpleNamespace) -> str:
    return f"/api/v1/schools/{data.school.id}/groups/{data.group.id}/lessons/"


def lesson_detail_url(data: SimpleNamespace, lesson: Lesson) -> str:
    return f"{lesson_list_url(data)}{lesson.id}/"


def state_list_url(data: SimpleNamespace, lesson: Lesson) -> str:
    return f"{lesson_detail_url(data, lesson)}states/"


def create_lesson(data: SimpleNamespace, subject: Subject) -> Lesson:
    return Lesson.objects.create(
        group=data.group,
        subject=subject,
        date="2026-09-15",
    )


@pytest.mark.django_db
def test_journal_endpoints_require_authentication(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    lesson = create_lesson(journal_data, journal_data.painting)

    list_response = api_client.get(lesson_list_url(journal_data))
    assert list_response.status_code == 401
    assert list_response.json()["error"]["code"] == "authentication_required"
    assert api_client.get(lesson_detail_url(journal_data, lesson)).status_code == 401
    assert api_client.get(state_list_url(journal_data, lesson)).status_code == 401


@pytest.mark.django_db
def test_administrator_creates_and_updates_lesson_with_ordered_topics(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(journal_data.admin)

    created = api_client.post(
        lesson_list_url(journal_data),
        {
            "subject_id": str(journal_data.painting.id),
            "date": "2026-09-15",
            "start_time": "14:00:00",
            "end_time": "15:30:00",
            "topic_ids": [
                str(journal_data.painting_topic.id),
                str(journal_data.second_painting_topic.id),
            ],
        },
        format="json",
    )

    assert created.status_code == 201
    lesson = Lesson.objects.get(pk=created.json()["id"])
    assert list(lesson.topic_links.values_list("topic_id", flat=True)) == [
        journal_data.painting_topic.id,
        journal_data.second_painting_topic.id,
    ]

    updated = api_client.patch(
        lesson_detail_url(journal_data, lesson),
        {"topic_ids": [str(journal_data.second_painting_topic.id)]},
        format="json",
    )

    assert updated.status_code == 200
    assert updated.json()["topics"] == [
        {
            "topic_id": str(journal_data.second_painting_topic.id),
            "topic_name": journal_data.second_painting_topic.name,
            "position": 0,
        }
    ]


@pytest.mark.django_db
def test_lesson_list_filters_dates_and_subject(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(journal_data.admin)
    included = create_lesson(journal_data, journal_data.painting)
    Lesson.objects.create(
        group=journal_data.group,
        subject=journal_data.painting,
        date="2026-10-01",
    )
    create_lesson(journal_data, journal_data.drawing)

    response = api_client.get(
        lesson_list_url(journal_data),
        {
            "date_from": "2026-09-01",
            "date_to": "2026-09-30",
            "subject_id": str(journal_data.painting.id),
        },
    )

    assert response.status_code == 200
    assert [item["id"] for item in response.json()] == [str(included.id)]


@pytest.mark.django_db
def test_teacher_lists_and_edits_only_assigned_subject(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    TeachingAssignment.objects.create(
        membership=journal_data.teacher_membership,
        group=journal_data.group,
        subject=journal_data.painting,
    )
    painting_lesson = create_lesson(journal_data, journal_data.painting)
    drawing_lesson = create_lesson(journal_data, journal_data.drawing)
    api_client.force_authenticate(journal_data.teacher)

    listed = api_client.get(lesson_list_url(journal_data))
    allowed = api_client.patch(
        lesson_detail_url(journal_data, painting_lesson),
        {"custom_topic_name": "Still life"},
        format="json",
    )
    denied = api_client.get(lesson_detail_url(journal_data, drawing_lesson))
    spoofed = api_client.get(
        lesson_detail_url(journal_data, drawing_lesson),
        {"subject_id": str(journal_data.painting.id)},
    )

    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [str(painting_lesson.id)]
    assert allowed.status_code == 200
    assert denied.status_code == 403
    assert spoofed.status_code == 403


@pytest.mark.django_db
def test_teacher_cannot_create_lesson_for_unassigned_subject(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    TeachingAssignment.objects.create(
        membership=journal_data.teacher_membership,
        group=journal_data.group,
        subject=journal_data.painting,
    )
    api_client.force_authenticate(journal_data.teacher)

    response = api_client.post(
        lesson_list_url(journal_data),
        {
            "subject_id": str(journal_data.drawing.id),
            "date": "2026-09-15",
        },
        format="json",
    )
    malformed = api_client.post(
        lesson_list_url(journal_data),
        {"subject_id": "not-a-uuid", "date": "2026-09-15"},
        format="json",
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "permission_denied"
    assert malformed.status_code == 400
    assert malformed.json()["error"]["code"] == "validation_error"
    assert not Lesson.objects.exists()


@pytest.mark.django_db
def test_group_wide_assignment_grants_all_lesson_subjects(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    TeachingAssignment.objects.create(
        membership=journal_data.teacher_membership,
        group=journal_data.group,
        subject=None,
    )
    create_lesson(journal_data, journal_data.painting)
    create_lesson(journal_data, journal_data.drawing)
    api_client.force_authenticate(journal_data.teacher)

    response = api_client.get(lesson_list_url(journal_data))

    assert response.status_code == 200
    assert {item["subject_id"] for item in response.json()} == {
        str(journal_data.painting.id),
        str(journal_data.drawing.id),
    }


@pytest.mark.django_db
def test_unassigned_or_inactive_teacher_cannot_access_group_journal(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(journal_data.unassigned_teacher)
    assert api_client.get(lesson_list_url(journal_data)).status_code == 403

    TeachingAssignment.objects.create(
        membership=journal_data.unassigned_membership,
        group=journal_data.group,
        subject=None,
    )
    journal_data.unassigned_membership.is_active = False
    journal_data.unassigned_membership.save(update_fields=["is_active"])

    assert api_client.get(lesson_list_url(journal_data)).status_code == 403


@pytest.mark.django_db
def test_other_school_cannot_access_or_select_group(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(journal_data.outsider)

    forbidden = api_client.get(lesson_list_url(journal_data))
    mismatched_path = api_client.get(
        f"/api/v1/schools/{journal_data.other_school.id}/groups/{journal_data.group.id}/lessons/"
    )

    assert forbidden.status_code == 403
    assert mismatched_path.status_code == 404


@pytest.mark.django_db
def test_lesson_rejects_topic_from_another_subject(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(journal_data.admin)

    response = api_client.post(
        lesson_list_url(journal_data),
        {
            "subject_id": str(journal_data.painting.id),
            "date": "2026-09-15",
            "topic_ids": [str(journal_data.drawing_topic.id)],
        },
        format="json",
    )

    assert response.status_code == 400
    assert "topic_ids" in response.json()["error"]["fields"]
    assert not LessonTopic.objects.exists()


@pytest.mark.django_db
def test_assigned_teacher_creates_and_updates_student_state(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    TeachingAssignment.objects.create(
        membership=journal_data.teacher_membership,
        group=journal_data.group,
        subject=journal_data.painting,
    )
    lesson = create_lesson(journal_data, journal_data.painting)
    api_client.force_authenticate(journal_data.teacher)

    created = api_client.post(
        state_list_url(journal_data, lesson),
        {
            "student_id": str(journal_data.student.id),
            "grade": 4,
            "is_present": True,
            "homework_points": 80,
            "comment": "Good progress",
        },
        format="json",
    )

    assert created.status_code == 201
    state = StudentLessonState.objects.get(pk=created.json()["id"])
    detail_url = f"{state_list_url(journal_data, lesson)}{state.id}/"
    updated = api_client.patch(detail_url, {"grade": 5}, format="json")
    duplicate = api_client.post(
        state_list_url(journal_data, lesson),
        {"student_id": str(journal_data.student.id)},
        format="json",
    )

    assert updated.status_code == 200
    assert updated.json()["grade"] == 5
    assert duplicate.status_code == 400


@pytest.mark.django_db
def test_state_rejects_student_not_enrolled_on_lesson_date(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    lesson = create_lesson(journal_data, journal_data.painting)
    api_client.force_authenticate(journal_data.admin)

    response = api_client.post(
        state_list_url(journal_data, lesson),
        {"student_id": str(journal_data.unenrolled_student.id)},
        format="json",
    )
    cross_school = api_client.post(
        state_list_url(journal_data, lesson),
        {"student_id": str(journal_data.other_school_student.id)},
        format="json",
    )

    assert response.status_code == 400
    assert cross_school.status_code == 400
    assert not StudentLessonState.objects.exists()


@pytest.mark.django_db
def test_subject_spoof_cannot_grant_state_access(
    api_client: APIClient,
    journal_data: SimpleNamespace,
) -> None:
    TeachingAssignment.objects.create(
        membership=journal_data.teacher_membership,
        group=journal_data.group,
        subject=journal_data.painting,
    )
    drawing_lesson = create_lesson(journal_data, journal_data.drawing)
    api_client.force_authenticate(journal_data.teacher)

    response = api_client.post(
        state_list_url(journal_data, drawing_lesson),
        {
            "student_id": str(journal_data.student.id),
            "subject_id": str(journal_data.painting.id),
        },
        format="json",
    )

    assert response.status_code == 403
    assert not StudentLessonState.objects.exists()
