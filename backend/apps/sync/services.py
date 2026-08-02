import hashlib
import json
from dataclasses import dataclass
from typing import Any

from django.core.exceptions import ValidationError as DjangoValidationError
from django.core.serializers.json import DjangoJSONEncoder
from django.db import IntegrityError, transaction
from django.db.models import Exists, OuterRef, Q, QuerySet
from rest_framework import serializers

from apps.education.models import Student
from apps.journal.models import Lesson, StudentLessonState
from apps.journal.serializers import StudentLessonStateSerializer
from apps.journal.services import (
    StateVersionConflict,
    delete_student_lesson_state,
    student_lesson_state_sync_payload,
)
from apps.schools.access import GroupAction, active_membership, has_group_action
from apps.schools.models import Membership, TeachingAssignment
from apps.sync.models import ChangeEvent, SyncClient, SyncCommand


@dataclass(frozen=True)
class CommandRejected(Exception):
    code: str
    message: str
    fields: Any = None


@dataclass(frozen=True)
class CommandConflict(Exception):
    current: StudentLessonState | None
    message: str = "The server record has changed since the client's base version."


def _json_primitive(value: Any) -> Any:
    return json.loads(json.dumps(value, cls=DjangoJSONEncoder, ensure_ascii=False))


def _operation_checksum(operation: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    primitive = _json_primitive(operation)
    canonical = json.dumps(
        primitive,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest(), primitive


def _result(
    operation: dict[str, Any],
    *,
    status: str,
    version: int | None = None,
    payload: dict[str, Any] | None = None,
    error: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "operationId": str(operation["operationId"]),
        "status": status,
        "entityType": operation["entityType"],
        "entityId": str(operation["entityId"]),
        "version": version,
        "payload": payload,
        "error": error,
    }


def _stored_result(command: SyncCommand, operation: dict[str, Any]) -> dict[str, Any]:
    result = dict(command.result)
    if command.status == SyncCommand.Status.APPLIED:
        result["status"] = "duplicate"
    return result or _result(
        operation,
        status=command.status,
        error={"code": "stored_result_missing", "message": "Stored result is unavailable."},
    )


def _client_for_operation(user: Any, client_id: Any) -> SyncClient:
    client, _ = SyncClient.objects.get_or_create(id=client_id, defaults={"user": user})
    client = SyncClient.objects.select_for_update().get(pk=client.pk)
    if client.user_id != user.id:
        raise CommandRejected(
            "client_ownership_conflict",
            "This clientId is already registered to another user.",
        )
    client.save(update_fields=["updated_at"])
    return client


def _dependencies_applied(
    *,
    school: Any,
    client: SyncClient,
    dependencies: list[Any],
) -> bool:
    if not dependencies:
        return True
    applied = SyncCommand.objects.filter(
        school=school,
        client=client,
        operation_id__in=dependencies,
        status=SyncCommand.Status.APPLIED,
    ).count()
    return applied == len(set(dependencies))


def _rest_payload(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "student_id": payload["studentId"],
        "grade": payload["grade"],
        "is_present": payload["isPresent"],
        "is_excused_absence": payload["isExcusedAbsence"],
        "homework_points": payload["homeworkPoints"],
        "comment": payload["comment"],
        "note": payload["note"],
    }


def _visible_state(state: StudentLessonState | None) -> tuple[int | None, Any]:
    if state is None:
        return None, None
    return state.version, student_lesson_state_sync_payload(state)


def _authorize_state(user: Any, state: StudentLessonState) -> None:
    if not has_group_action(
        user,
        state.lesson.group,
        GroupAction.EDIT_JOURNAL,
        subject=state.lesson.subject,
    ):
        raise CommandRejected(
            "permission_denied",
            "The user cannot edit this lesson state.",
        )


def _handle_state_upsert(
    *,
    user: Any,
    school: Any,
    operation: dict[str, Any],
) -> tuple[int, dict[str, Any]]:
    payload = operation["payload"]
    lesson = (
        Lesson.objects.select_related("group__academic_year__school", "subject")
        .filter(pk=payload["lessonId"], group__academic_year__school=school)
        .first()
    )
    if lesson is None:
        raise CommandRejected("not_found", "The lesson was not found in this school.")
    if not has_group_action(
        user,
        lesson.group,
        GroupAction.EDIT_JOURNAL,
        subject=lesson.subject,
    ):
        raise CommandRejected("permission_denied", "The user cannot edit this lesson.")

    student = Student.objects.filter(pk=payload["studentId"], school=school).first()
    if student is None:
        raise CommandRejected("not_found", "The student was not found in this school.")

    state = (
        StudentLessonState.objects.select_related(
            "lesson__group__academic_year__school",
            "lesson__subject",
            "student",
        )
        .filter(pk=operation["entityId"])
        .first()
    )
    pair_state = (
        StudentLessonState.objects.select_related(
            "lesson__group__academic_year__school",
            "lesson__subject",
            "student",
        )
        .filter(lesson=lesson, student=student)
        .first()
    )

    base_version = operation["baseVersion"]
    if base_version is None:
        if state is not None or pair_state is not None:
            raise CommandConflict(state or pair_state, "The state already exists.")
        serializer = StudentLessonStateSerializer(
            data=_rest_payload(payload),
            context={
                "lesson": lesson,
                "actor": user,
                "entity_id": operation["entityId"],
            },
        )
    else:
        if state is None:
            raise CommandConflict(None, "The state no longer exists on the server.")
        _authorize_state(user, state)
        if state.lesson_id != lesson.id or state.student_id != student.id:
            raise CommandRejected(
                "immutable_identity",
                "lessonId and studentId cannot change for an existing state.",
            )
        serializer = StudentLessonStateSerializer(
            state,
            data=_rest_payload(payload),
            context={
                "lesson": lesson,
                "actor": user,
                "expected_version": base_version,
            },
        )

    serializer.is_valid(raise_exception=True)
    try:
        saved = serializer.save()
    except StateVersionConflict as error:
        raise CommandConflict(error.current) from error
    return saved.version, student_lesson_state_sync_payload(saved)


def _handle_state_delete(
    *,
    user: Any,
    school: Any,
    operation: dict[str, Any],
) -> tuple[int, None]:
    state = (
        StudentLessonState.objects.select_related(
            "lesson__group__academic_year__school",
            "lesson__subject",
            "student",
        )
        .filter(
            pk=operation["entityId"],
            lesson__group__academic_year__school=school,
        )
        .first()
    )
    if state is None:
        raise CommandConflict(None, "The state no longer exists on the server.")
    _authorize_state(user, state)
    try:
        version = delete_student_lesson_state(
            instance=state,
            actor=user,
            expected_version=operation["baseVersion"],
        )
    except StateVersionConflict as error:
        raise CommandConflict(error.current) from error
    return version, None


def _handle_operation(
    *,
    user: Any,
    school: Any,
    operation: dict[str, Any],
) -> tuple[int, dict[str, Any] | None]:
    if operation["schoolId"] != school.id:
        raise CommandRejected(
            "school_mismatch",
            "The operation schoolId does not match the endpoint school.",
        )
    if operation["entityType"] != "student_lesson_state":
        raise CommandRejected("unsupported_entity", "The entity type is not supported.")
    if operation["action"] == "upsert":
        return _handle_state_upsert(user=user, school=school, operation=operation)
    if operation["action"] == "delete":
        return _handle_state_delete(user=user, school=school, operation=operation)
    raise CommandRejected("unsupported_action", "The action is not supported.")


def _validation_fields(error: Any) -> Any:
    return _json_primitive(getattr(error, "detail", getattr(error, "message_dict", {})))


@transaction.atomic
def process_operation(*, user: Any, school: Any, operation: dict[str, Any]) -> dict[str, Any]:
    checksum, primitive = _operation_checksum(operation)
    try:
        client = _client_for_operation(user, operation["clientId"])
    except CommandRejected as error:
        return _result(
            operation,
            status="rejected",
            error={"code": error.code, "message": error.message, "fields": error.fields or {}},
        )

    command = SyncCommand.objects.filter(
        school=school,
        client=client,
        operation_id=operation["operationId"],
    ).first()
    if command is not None:
        if command.checksum != checksum:
            return _result(
                operation,
                status="rejected",
                error={
                    "code": "idempotency_conflict",
                    "message": "operationId was already used with different content.",
                    "fields": {},
                },
            )
        if command.status != SyncCommand.Status.BLOCKED:
            return _stored_result(command, operation)
    else:
        if SyncCommand.objects.filter(
            school=school,
            client=client,
            client_sequence=operation["clientSequence"],
        ).exists():
            return _result(
                operation,
                status="rejected",
                error={
                    "code": "sequence_conflict",
                    "message": "clientSequence was already used by another operation.",
                    "fields": {},
                },
            )
        command = SyncCommand.objects.create(
            school=school,
            client=client,
            operation_id=operation["operationId"],
            client_sequence=operation["clientSequence"],
            entity_type=operation["entityType"],
            entity_id=operation["entityId"],
            action=operation["action"],
            base_version=operation["baseVersion"],
            checksum=checksum,
            request_payload=primitive,
            client_created_at=operation["createdAt"],
        )

    if not _dependencies_applied(
        school=school,
        client=client,
        dependencies=operation["dependsOn"],
    ):
        result = _result(
            operation,
            status="blocked",
            error={
                "code": "dependency_not_applied",
                "message": "At least one dependsOn operation has not been applied.",
                "fields": {},
            },
        )
        command.status = SyncCommand.Status.BLOCKED
        command.result = result
        command.save(update_fields=["status", "result", "updated_at"])
        return result

    try:
        with transaction.atomic():
            version, payload = _handle_operation(user=user, school=school, operation=operation)
    except CommandConflict as error:
        version, payload = _visible_state(error.current)
        result = _result(
            operation,
            status="conflict",
            version=version,
            payload=payload,
            error={"code": "version_conflict", "message": error.message, "fields": {}},
        )
        command.status = SyncCommand.Status.CONFLICT
    except CommandRejected as error:
        result = _result(
            operation,
            status="rejected",
            error={"code": error.code, "message": error.message, "fields": error.fields or {}},
        )
        command.status = SyncCommand.Status.REJECTED
    except (serializers.ValidationError, DjangoValidationError) as error:
        result = _result(
            operation,
            status="rejected",
            error={
                "code": "validation_error",
                "message": "The command payload failed domain validation.",
                "fields": _validation_fields(error),
            },
        )
        command.status = SyncCommand.Status.REJECTED
    except IntegrityError:
        result = _result(
            operation,
            status="rejected",
            error={
                "code": "integrity_error",
                "message": "The command conflicts with an existing server record.",
                "fields": {},
            },
        )
        command.status = SyncCommand.Status.REJECTED
    else:
        result = _result(
            operation,
            status="applied",
            version=version,
            payload=payload,
        )
        command.status = SyncCommand.Status.APPLIED

    command.result = _json_primitive(result)
    command.save(update_fields=["status", "result", "updated_at"])
    return result


def process_batch(*, user: Any, school: Any, operations: list[dict[str, Any]]) -> list[Any]:
    return [
        process_operation(user=user, school=school, operation=operation) for operation in operations
    ]


def visible_change_events(*, user: Any, school: Any) -> QuerySet[ChangeEvent]:
    queryset = ChangeEvent.objects.filter(school=school)
    if getattr(user, "is_superuser", False):
        return queryset

    membership = active_membership(user, school)
    if membership is None:
        return queryset.none()
    if membership.role == Membership.Role.ADMIN:
        return queryset

    assignments = TeachingAssignment.objects.filter(
        membership=membership,
        group_id=OuterRef("group_id"),
    ).filter(Q(subject_id__isnull=True) | Q(subject_id=OuterRef("subject_id")))
    return queryset.annotate(is_visible=Exists(assignments)).filter(is_visible=True)
