import json
from pathlib import Path
from types import SimpleNamespace
from uuid import UUID, uuid4

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from rest_framework.test import APIClient

from apps.journal.models import StudentLessonState
from apps.sync.models import ChangeEvent, SyncCommand

SCHEMA_PATH = Path(__file__).parents[1] / "schemas" / "sync-protocol-v1.schema.json"


def commands_url(data: SimpleNamespace) -> str:
    return f"/api/v1/schools/{data.school.id}/sync/commands/"


def changes_url(data: SimpleNamespace) -> str:
    return f"/api/v1/schools/{data.school.id}/sync/changes/"


def state_list_url(data: SimpleNamespace, lesson_id: UUID) -> str:
    return f"/api/v1/schools/{data.school.id}/groups/{data.group.id}/lessons/{lesson_id}/states/"


def operation(
    data: SimpleNamespace,
    *,
    lesson_id: UUID | None = None,
    entity_id: UUID | None = None,
    operation_id: UUID | None = None,
    client_id: UUID | None = None,
    sequence: int = 1,
    base_version: int | None = None,
    action: str = "upsert",
    grade: int | None = 4,
    is_present: bool = True,
    is_excused_absence: bool = False,
    depends_on: list[UUID] | None = None,
) -> dict:
    payload = {}
    if action == "upsert":
        payload = {
            "lessonId": str(lesson_id or data.painting_lesson.id),
            "studentId": str(data.student.id),
            "grade": grade,
            "isPresent": is_present,
            "isExcusedAbsence": is_excused_absence,
            "homeworkPoints": 80,
            "comment": "Good progress",
            "note": "",
        }
    return {
        "protocolVersion": 1,
        "operationId": str(operation_id or uuid4()),
        "clientId": str(client_id or uuid4()),
        "clientSequence": sequence,
        "schoolId": str(data.school.id),
        "entityType": "student_lesson_state",
        "entityId": str(entity_id or uuid4()),
        "action": action,
        "baseVersion": base_version,
        "dependsOn": [str(item) for item in (depends_on or [])],
        "payload": payload,
        "createdAt": "2026-08-02T12:00:00Z",
    }


def post_operation(api_client: APIClient, data: SimpleNamespace, item: dict):
    return api_client.post(commands_url(data), {"operations": [item]}, format="json")


@pytest.mark.django_db
def test_sync_endpoints_require_authentication(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    item = operation(sync_data)

    command_response = post_operation(api_client, sync_data, item)
    change_response = api_client.get(changes_url(sync_data))

    assert command_response.status_code == 401
    assert change_response.status_code == 401


@pytest.mark.django_db
def test_applies_and_deduplicates_state_create(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.teacher)
    entity_id = uuid4()
    item = operation(sync_data, entity_id=entity_id)

    applied = post_operation(api_client, sync_data, item)
    duplicate = post_operation(api_client, sync_data, item)

    assert applied.status_code == 200
    assert applied.json()["results"][0]["status"] == "applied"
    assert applied.json()["results"][0]["version"] == 1
    assert duplicate.json()["results"][0]["status"] == "duplicate"
    state = StudentLessonState.objects.get(pk=entity_id)
    assert state.grade == 4
    assert state.version == 1
    assert SyncCommand.objects.count() == 1
    assert ChangeEvent.objects.count() == 1


@pytest.mark.django_db
def test_rejects_operation_id_reuse_with_different_content(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.teacher)
    item = operation(sync_data)
    changed = {**item, "payload": {**item["payload"], "grade": 5}}

    assert post_operation(api_client, sync_data, item).json()["results"][0]["status"] == "applied"
    rejected = post_operation(api_client, sync_data, changed)

    result = rejected.json()["results"][0]
    assert result["status"] == "rejected"
    assert result["error"]["code"] == "idempotency_conflict"
    assert StudentLessonState.objects.get(pk=item["entityId"]).grade == 4
    assert SyncCommand.objects.count() == 1
    assert ChangeEvent.objects.count() == 1


@pytest.mark.django_db
def test_returns_conflict_for_stale_base_version_after_rest_update(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    client_id = uuid4()
    entity_id = uuid4()
    created = operation(sync_data, client_id=client_id, entity_id=entity_id)
    created_result = post_operation(api_client, sync_data, created).json()["results"][0]
    assert created_result["status"] == "applied"

    detail_url = f"{state_list_url(sync_data, sync_data.painting_lesson.id)}{entity_id}/"
    rest_update = api_client.patch(detail_url, {"grade": 5}, format="json")
    stale = operation(
        sync_data,
        client_id=client_id,
        entity_id=entity_id,
        sequence=2,
        base_version=1,
        grade=3,
    )
    conflict = post_operation(api_client, sync_data, stale)

    assert rest_update.status_code == 200
    assert rest_update.json()["version"] == 2
    result = conflict.json()["results"][0]
    assert result["status"] == "conflict"
    assert result["version"] == 2
    assert result["payload"]["grade"] == 5
    assert StudentLessonState.objects.get(pk=entity_id).grade == 5
    assert ChangeEvent.objects.count() == 2


@pytest.mark.django_db
def test_revoked_teacher_cannot_submit_sync_batch(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.revoked_teacher)

    response = post_operation(api_client, sync_data, operation(sync_data))

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "permission_denied"
    assert not StudentLessonState.objects.exists()
    assert not ChangeEvent.objects.exists()


@pytest.mark.django_db
def test_school_mismatch_is_rejected_per_operation(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    item = operation(sync_data)
    item["schoolId"] = str(sync_data.other_school.id)

    response = post_operation(api_client, sync_data, item)

    result = response.json()["results"][0]
    assert result["status"] == "rejected"
    assert result["error"]["code"] == "school_mismatch"
    assert not StudentLessonState.objects.exists()


@pytest.mark.django_db
def test_partial_batch_commits_valid_command_and_rejects_domain_error(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    client_id = uuid4()
    valid = operation(sync_data, client_id=client_id, sequence=1)
    inconsistent = operation(
        sync_data,
        lesson_id=sync_data.drawing_lesson.id,
        client_id=client_id,
        sequence=2,
        is_present=True,
        is_excused_absence=True,
    )

    response = api_client.post(
        commands_url(sync_data),
        {"operations": [valid, inconsistent]},
        format="json",
    )

    assert response.status_code == 200
    assert [item["status"] for item in response.json()["results"]] == [
        "applied",
        "rejected",
    ]
    assert StudentLessonState.objects.count() == 1
    assert ChangeEvent.objects.count() == 1
    assert SyncCommand.objects.count() == 2


@pytest.mark.django_db
def test_delete_creates_tombstone_event_and_is_idempotent(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    client_id = uuid4()
    entity_id = uuid4()
    create = operation(sync_data, client_id=client_id, entity_id=entity_id, sequence=1)
    assert post_operation(api_client, sync_data, create).json()["results"][0]["status"] == "applied"
    delete = operation(
        sync_data,
        client_id=client_id,
        entity_id=entity_id,
        sequence=2,
        base_version=1,
        action="delete",
    )

    deleted = post_operation(api_client, sync_data, delete)
    duplicate = post_operation(api_client, sync_data, delete)

    assert deleted.json()["results"][0]["status"] == "applied"
    assert deleted.json()["results"][0]["version"] == 2
    assert duplicate.json()["results"][0]["status"] == "duplicate"
    assert not StudentLessonState.objects.filter(pk=entity_id).exists()
    assert list(ChangeEvent.objects.values_list("action", "version")) == [
        ("upsert", 1),
        ("delete", 2),
    ]


@pytest.mark.django_db
def test_change_feed_filters_subject_access_and_pages_by_cursor(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    painting = api_client.post(
        state_list_url(sync_data, sync_data.painting_lesson.id),
        {"student_id": str(sync_data.student.id), "grade": 4},
        format="json",
    )
    drawing = api_client.post(
        state_list_url(sync_data, sync_data.drawing_lesson.id),
        {"student_id": str(sync_data.student.id), "grade": 5},
        format="json",
    )
    assert painting.status_code == 201
    assert drawing.status_code == 201

    api_client.force_authenticate(sync_data.teacher)
    first_page = api_client.get(changes_url(sync_data), {"cursor": 0, "limit": 1})

    assert first_page.status_code == 200
    assert len(first_page.json()["changes"]) == 1
    assert first_page.json()["changes"][0]["payload"]["grade"] == 4
    assert first_page.json()["hasMore"] is False
    cursor = first_page.json()["nextCursor"]
    assert api_client.get(changes_url(sync_data), {"cursor": cursor}).json()["changes"] == []

    api_client.force_authenticate(sync_data.outsider)
    assert api_client.get(changes_url(sync_data)).status_code == 403


@pytest.mark.django_db
def test_missing_dependency_is_blocked_and_can_be_retried(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    dependency_id = uuid4()
    client_id = uuid4()
    blocked = operation(
        sync_data,
        client_id=client_id,
        sequence=2,
        depends_on=[dependency_id],
    )
    dependency = operation(
        sync_data,
        lesson_id=sync_data.drawing_lesson.id,
        operation_id=dependency_id,
        client_id=client_id,
        sequence=1,
    )

    first = post_operation(api_client, sync_data, blocked)
    assert first.json()["results"][0]["status"] == "blocked"
    dependency_result = post_operation(api_client, sync_data, dependency).json()["results"][0]
    assert dependency_result["status"] == "applied"
    retried = post_operation(api_client, sync_data, blocked)

    assert retried.json()["results"][0]["status"] == "applied"
    assert StudentLessonState.objects.count() == 2


@pytest.mark.django_db
def test_json_schema_accepts_contract_fixture_and_rejects_extra_fields(
    sync_data: SimpleNamespace,
) -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    fixture = {"operations": [operation(sync_data)]}

    validator.validate(fixture)
    fixture["unexpected"] = True

    assert list(validator.iter_errors(fixture))


@pytest.mark.django_db
def test_command_endpoint_enforces_json_schema(
    api_client: APIClient,
    sync_data: SimpleNamespace,
) -> None:
    api_client.force_authenticate(sync_data.admin)
    malformed = {"operations": [operation(sync_data)], "unexpected": True}

    response = api_client.post(commands_url(sync_data), malformed, format="json")

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "validation_error"
    assert response.json()["error"]["fields"]["schema"]
    assert not SyncCommand.objects.exists()
    assert not StudentLessonState.objects.exists()
