from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel


class ImportBatch(TimeStampedModel):
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        RUNNING = "running", "Running"
        SUCCEEDED = "succeeded", "Succeeded"
        FAILED = "failed", "Failed"

    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="import_batches",
    )
    export_id = models.CharField(max_length=255, unique=True)
    checksum = models.CharField(max_length=64)
    format = models.CharField(max_length=100)
    format_version = models.PositiveIntegerField()
    source = models.JSONField(default=dict)
    exported_at = models.DateTimeField(null=True, blank=True)
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.PENDING,
    )
    counts = models.JSONField(default=dict)
    report = models.JSONField(default=dict)
    finished_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(status__in=("pending", "running", "succeeded", "failed")),
                name="import_batch_status_valid",
            ),
            models.CheckConstraint(
                condition=Q(format_version__gt=0),
                name="import_batch_format_version_positive",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "status", "created_at"],
                name="import_school_status_time_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.export_id}: {self.status}"


class LegacyObjectMap(TimeStampedModel):
    import_batch = models.ForeignKey(
        ImportBatch,
        on_delete=models.CASCADE,
        related_name="object_maps",
    )
    entity_type = models.CharField(max_length=64)
    legacy_local_id = models.PositiveBigIntegerField()
    server_model = models.CharField(max_length=100)
    server_object_id = models.UUIDField()

    class Meta:
        ordering = ["entity_type", "legacy_local_id", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["import_batch", "entity_type", "legacy_local_id"],
                name="legacy_object_map_unique_source",
            ),
            models.CheckConstraint(
                condition=Q(legacy_local_id__gt=0),
                name="legacy_object_map_id_positive",
            ),
        ]
        indexes = [
            models.Index(
                fields=["import_batch", "entity_type", "legacy_local_id"],
                name="legacy_object_source_idx",
            ),
            models.Index(
                fields=["server_model", "server_object_id"],
                name="legacy_object_target_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.entity_type}:{self.legacy_local_id} → {self.server_model}"
