from dataclasses import dataclass
from typing import Any
from uuid import UUID

from django.db import transaction

from apps.journal.models import StudentLessonState


@dataclass(frozen=True)
class StateVersionConflict(Exception):
    current: StudentLessonState | None


def student_lesson_state_sync_payload(state: StudentLessonState) -> dict[str, Any]:
    return {
        "entityId": str(state.id),
        "lessonId": str(state.lesson_id),
        "studentId": str(state.student_id),
        "grade": state.grade,
        "isPresent": state.is_present,
        "isExcusedAbsence": state.is_excused_absence,
        "homeworkPoints": state.homework_points,
        "comment": state.comment,
        "note": state.note,
        "version": state.version,
        "createdAt": state.created_at.isoformat().replace("+00:00", "Z"),
        "updatedAt": state.updated_at.isoformat().replace("+00:00", "Z"),
    }


def _record_state_change(
    state: StudentLessonState,
    *,
    actor: Any,
    action: str,
    version: int,
    payload: dict[str, Any],
) -> None:
    from apps.sync.events import record_change

    record_change(
        school=state.lesson.group.academic_year.school,
        group=state.lesson.group,
        subject=state.lesson.subject,
        actor=actor,
        entity_type="student_lesson_state",
        entity_id=state.id,
        action=action,
        version=version,
        payload=payload,
    )


@transaction.atomic
def create_student_lesson_state(
    *,
    lesson: Any,
    validated_data: dict[str, Any],
    actor: Any,
    entity_id: UUID | None = None,
) -> StudentLessonState:
    state_kwargs = {"lesson": lesson, **validated_data}
    if entity_id is not None:
        state_kwargs["id"] = entity_id
    state = StudentLessonState(**state_kwargs)
    state.full_clean()
    state.save(force_insert=True)
    _record_state_change(
        state,
        actor=actor,
        action="upsert",
        version=state.version,
        payload=student_lesson_state_sync_payload(state),
    )
    return state


@transaction.atomic
def update_student_lesson_state(
    *,
    instance: StudentLessonState,
    validated_data: dict[str, Any],
    actor: Any,
    expected_version: int | None = None,
) -> StudentLessonState:
    state = (
        StudentLessonState.objects.select_for_update()
        .select_related("lesson__group__academic_year__school", "lesson__subject")
        .get(pk=instance.pk)
    )
    if expected_version is not None and state.version != expected_version:
        raise StateVersionConflict(state)

    for field, value in validated_data.items():
        setattr(state, field, value)
    state.version += 1
    state.full_clean()
    state.save()
    _record_state_change(
        state,
        actor=actor,
        action="upsert",
        version=state.version,
        payload=student_lesson_state_sync_payload(state),
    )
    return state


@transaction.atomic
def delete_student_lesson_state(
    *,
    instance: StudentLessonState,
    actor: Any,
    expected_version: int | None = None,
) -> int:
    state = (
        StudentLessonState.objects.select_for_update()
        .select_related("lesson__group__academic_year__school", "lesson__subject")
        .get(pk=instance.pk)
    )
    if expected_version is not None and state.version != expected_version:
        raise StateVersionConflict(state)

    tombstone_version = state.version + 1
    _record_state_change(
        state,
        actor=actor,
        action="delete",
        version=tombstone_version,
        payload={},
    )
    state.delete()
    return tombstone_version
