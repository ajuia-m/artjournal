from typing import Any
from uuid import UUID

from apps.sync.models import ChangeEvent


def record_change(
    *,
    school: Any,
    group: Any,
    subject: Any,
    actor: Any,
    entity_type: str,
    entity_id: UUID,
    action: str,
    version: int,
    payload: dict[str, Any],
) -> ChangeEvent:
    authenticated_actor = actor if getattr(actor, "is_authenticated", False) else None
    return ChangeEvent.objects.create(
        school=school,
        group=group,
        subject=subject,
        actor=authenticated_actor,
        entity_type=entity_type,
        entity_id=entity_id,
        action=action,
        version=version,
        payload=payload,
    )
