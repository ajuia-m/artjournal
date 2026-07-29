from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import TimeStampedModel, UUIDModel


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


class LegacyAuditEntry(TimeStampedModel):
    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="legacy_audit_entries",
    )
    import_batch = models.ForeignKey(
        "imports.ImportBatch",
        on_delete=models.CASCADE,
        related_name="legacy_audit_entries",
    )
    legacy_local_id = models.PositiveBigIntegerField()
    occurred_at = models.DateTimeField()
    action = models.CharField(max_length=150)
    details = models.TextField()
    revert_data = models.TextField(blank=True)

    class Meta:
        ordering = ["occurred_at", "legacy_local_id", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["import_batch", "legacy_local_id"],
                name="legacy_audit_entry_unique_source",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "occurred_at"],
                name="legacy_audit_school_time_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.occurred_at}: {self.action} (legacy)"
