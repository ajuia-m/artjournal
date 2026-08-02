from django.conf import settings
from django.db import models

from apps.core.models import TimeStampedModel


class SyncClient(TimeStampedModel):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="sync_clients",
    )

    class Meta:
        ordering = ["created_at", "id"]

    def __str__(self) -> str:
        return f"{self.user_id}:{self.id}"


class SyncCommand(TimeStampedModel):
    class Status(models.TextChoices):
        PROCESSING = "processing", "Processing"
        APPLIED = "applied", "Applied"
        CONFLICT = "conflict", "Conflict"
        REJECTED = "rejected", "Rejected"
        BLOCKED = "blocked", "Blocked"

    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="sync_commands",
    )
    client = models.ForeignKey(
        SyncClient,
        on_delete=models.CASCADE,
        related_name="commands",
    )
    operation_id = models.UUIDField()
    client_sequence = models.PositiveBigIntegerField()
    entity_type = models.CharField(max_length=64)
    entity_id = models.UUIDField()
    action = models.CharField(max_length=16)
    base_version = models.PositiveBigIntegerField(null=True, blank=True)
    checksum = models.CharField(max_length=64)
    request_payload = models.JSONField()
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.PROCESSING,
    )
    result = models.JSONField(default=dict)
    client_created_at = models.DateTimeField()

    class Meta:
        ordering = ["created_at", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["school", "client", "operation_id"],
                name="sync_command_operation_unique",
            ),
            models.UniqueConstraint(
                fields=["school", "client", "client_sequence"],
                name="sync_command_sequence_unique",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "status", "created_at"],
                name="sync_cmd_school_status_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.school_id}:{self.client_id}:{self.operation_id}"


class ChangeEvent(models.Model):
    class Action(models.TextChoices):
        UPSERT = "upsert", "Upsert"
        DELETE = "delete", "Delete"

    sequence = models.BigAutoField(primary_key=True)
    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="change_events",
    )
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="change_events",
    )
    subject = models.ForeignKey(
        "curriculum.Subject",
        on_delete=models.CASCADE,
        related_name="change_events",
    )
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="sync_change_events",
    )
    entity_type = models.CharField(max_length=64)
    entity_id = models.UUIDField()
    action = models.CharField(max_length=16, choices=Action.choices)
    version = models.PositiveBigIntegerField()
    payload = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["sequence"]
        constraints = [
            models.UniqueConstraint(
                fields=["school", "entity_type", "entity_id", "version"],
                name="sync_change_entity_version_unique",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "sequence"],
                name="sync_change_school_cursor_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.school_id}:{self.sequence}:{self.entity_type}:{self.entity_id}"
