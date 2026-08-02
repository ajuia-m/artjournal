import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("curriculum", "0001_initial"),
        ("education", "0001_initial"),
        ("schools", "0002_membership_teachingassignment_and_more"),
    ]

    operations = [
        migrations.CreateModel(
            name="SyncClient",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                (
                    "user",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="sync_clients",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={"ordering": ["created_at", "id"]},
        ),
        migrations.CreateModel(
            name="ChangeEvent",
            fields=[
                ("sequence", models.BigAutoField(primary_key=True, serialize=False)),
                ("entity_type", models.CharField(max_length=64)),
                ("entity_id", models.UUIDField()),
                (
                    "action",
                    models.CharField(
                        choices=[("upsert", "Upsert"), ("delete", "Delete")],
                        max_length=16,
                    ),
                ),
                ("version", models.PositiveBigIntegerField()),
                ("payload", models.JSONField(default=dict)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                (
                    "actor",
                    models.ForeignKey(
                        blank=True,
                        null=True,
                        on_delete=django.db.models.deletion.SET_NULL,
                        related_name="sync_change_events",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "group",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="change_events",
                        to="education.group",
                    ),
                ),
                (
                    "school",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="change_events",
                        to="schools.school",
                    ),
                ),
                (
                    "subject",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="change_events",
                        to="curriculum.subject",
                    ),
                ),
            ],
            options={
                "ordering": ["sequence"],
                "indexes": [
                    models.Index(
                        fields=["school", "sequence"],
                        name="sync_change_school_cursor_idx",
                    )
                ],
                "constraints": [
                    models.UniqueConstraint(
                        fields=("school", "entity_type", "entity_id", "version"),
                        name="sync_change_entity_version_unique",
                    )
                ],
            },
        ),
        migrations.CreateModel(
            name="SyncCommand",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("operation_id", models.UUIDField()),
                ("client_sequence", models.PositiveBigIntegerField()),
                ("entity_type", models.CharField(max_length=64)),
                ("entity_id", models.UUIDField()),
                ("action", models.CharField(max_length=16)),
                ("base_version", models.PositiveBigIntegerField(blank=True, null=True)),
                ("checksum", models.CharField(max_length=64)),
                ("request_payload", models.JSONField()),
                (
                    "status",
                    models.CharField(
                        choices=[
                            ("processing", "Processing"),
                            ("applied", "Applied"),
                            ("conflict", "Conflict"),
                            ("rejected", "Rejected"),
                            ("blocked", "Blocked"),
                        ],
                        default="processing",
                        max_length=16,
                    ),
                ),
                ("result", models.JSONField(default=dict)),
                ("client_created_at", models.DateTimeField()),
                (
                    "client",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="commands",
                        to="sync.syncclient",
                    ),
                ),
                (
                    "school",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="sync_commands",
                        to="schools.school",
                    ),
                ),
            ],
            options={
                "ordering": ["created_at", "id"],
                "indexes": [
                    models.Index(
                        fields=["school", "status", "created_at"],
                        name="sync_cmd_school_status_idx",
                    )
                ],
                "constraints": [
                    models.UniqueConstraint(
                        fields=("school", "client", "operation_id"),
                        name="sync_command_operation_unique",
                    ),
                    models.UniqueConstraint(
                        fields=("school", "client", "client_sequence"),
                        name="sync_command_sequence_unique",
                    ),
                ],
            },
        ),
    ]
