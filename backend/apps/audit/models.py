from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import UUIDModel


class AuditEvent(UUIDModel):
    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="audit_events",
    )
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        related_name="audit_events",
        null=True,
        blank=True,
    )
    occurred_at = models.DateTimeField(default=timezone.now, editable=False)
    action = models.CharField(max_length=150)
    object_type = models.CharField(max_length=100, blank=True)
    object_id = models.UUIDField(null=True, blank=True)
    details = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ["-occurred_at", "id"]
        indexes = [
            models.Index(
                fields=["school", "occurred_at"],
                name="audit_event_school_time_idx",
            ),
            models.Index(
                fields=["school", "action"],
                name="audit_event_school_action_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.occurred_at}: {self.action}"
