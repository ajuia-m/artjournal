from rest_framework import serializers

from apps.sync.models import ChangeEvent


class StudentLessonStateSyncPayloadSerializer(serializers.Serializer):
    lessonId = serializers.UUIDField()
    studentId = serializers.UUIDField()
    grade = serializers.IntegerField(min_value=0, max_value=5, allow_null=True)
    isPresent = serializers.BooleanField()
    isExcusedAbsence = serializers.BooleanField()
    homeworkPoints = serializers.IntegerField(
        min_value=0,
        max_value=101,
        allow_null=True,
    )
    comment = serializers.CharField(allow_blank=True)
    note = serializers.CharField(allow_blank=True)


class SyncOperationSerializer(serializers.Serializer):
    protocolVersion = serializers.IntegerField(min_value=1, max_value=1)
    operationId = serializers.UUIDField()
    clientId = serializers.UUIDField()
    clientSequence = serializers.IntegerField(min_value=1)
    schoolId = serializers.UUIDField()
    entityType = serializers.ChoiceField(choices=("student_lesson_state",))
    entityId = serializers.UUIDField()
    action = serializers.ChoiceField(choices=("upsert", "delete"))
    baseVersion = serializers.IntegerField(min_value=1, allow_null=True)
    dependsOn = serializers.ListField(
        child=serializers.UUIDField(),
        allow_empty=True,
    )
    payload = serializers.JSONField()
    createdAt = serializers.DateTimeField()

    def validate(self, attrs):
        if attrs["operationId"] in attrs["dependsOn"]:
            raise serializers.ValidationError(
                {"dependsOn": "An operation cannot depend on itself."}
            )
        if len(attrs["dependsOn"]) != len(set(attrs["dependsOn"])):
            raise serializers.ValidationError(
                {"dependsOn": "Dependency operation IDs must be unique."}
            )

        if attrs["action"] == "delete":
            if attrs["baseVersion"] is None:
                raise serializers.ValidationError(
                    {"baseVersion": "Delete requires the last confirmed version."}
                )
            if attrs["payload"] not in ({}, None):
                raise serializers.ValidationError(
                    {"payload": "Delete payload must be an empty object."}
                )
            attrs["payload"] = {}
            return attrs

        payload = StudentLessonStateSyncPayloadSerializer(data=attrs["payload"])
        payload.is_valid(raise_exception=True)
        attrs["payload"] = payload.validated_data
        return attrs


class SyncCommandBatchSerializer(serializers.Serializer):
    operations = SyncOperationSerializer(many=True, min_length=1, max_length=100)

    def validate_operations(self, operations):
        operation_keys = [
            (item["schoolId"], item["clientId"], item["operationId"]) for item in operations
        ]
        if len(operation_keys) != len(set(operation_keys)):
            raise serializers.ValidationError("operationId values must be unique in a batch.")

        sequence_keys = [
            (item["schoolId"], item["clientId"], item["clientSequence"]) for item in operations
        ]
        if len(sequence_keys) != len(set(sequence_keys)):
            raise serializers.ValidationError(
                "clientSequence values must be unique per client and school."
            )
        return operations


class SyncCommandResultSerializer(serializers.Serializer):
    operationId = serializers.UUIDField()
    status = serializers.ChoiceField(
        choices=("applied", "duplicate", "conflict", "rejected", "blocked")
    )
    entityType = serializers.CharField()
    entityId = serializers.UUIDField()
    version = serializers.IntegerField(allow_null=True)
    payload = serializers.JSONField(allow_null=True)
    error = serializers.JSONField(allow_null=True)


class SyncCommandBatchResponseSerializer(serializers.Serializer):
    results = SyncCommandResultSerializer(many=True)


class ChangeEventSerializer(serializers.ModelSerializer):
    cursor = serializers.CharField(source="sequence")
    schoolId = serializers.UUIDField(source="school_id")
    entityType = serializers.CharField(source="entity_type")
    entityId = serializers.UUIDField(source="entity_id")
    actorId = serializers.UUIDField(source="actor_id", allow_null=True)
    createdAt = serializers.DateTimeField(source="created_at")

    class Meta:
        model = ChangeEvent
        fields = (
            "cursor",
            "schoolId",
            "entityType",
            "entityId",
            "action",
            "version",
            "payload",
            "actorId",
            "createdAt",
        )


class ChangeFeedQuerySerializer(serializers.Serializer):
    cursor = serializers.IntegerField(min_value=0, default=0)
    limit = serializers.IntegerField(min_value=1, max_value=100, default=100)


class ChangeFeedResponseSerializer(serializers.Serializer):
    changes = ChangeEventSerializer(many=True)
    nextCursor = serializers.CharField()
    hasMore = serializers.BooleanField()
