import json

from django.shortcuts import get_object_or_404
from drf_spectacular.utils import extend_schema
from rest_framework import serializers, status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.serializers import ApiErrorSerializer
from apps.schools.access import SchoolAction
from apps.schools.models import School
from apps.schools.permissions import HasSchoolAction
from apps.sync.serializers import (
    ChangeEventSerializer,
    ChangeFeedQuerySerializer,
    ChangeFeedResponseSerializer,
    SyncCommandBatchResponseSerializer,
    SyncCommandBatchSerializer,
)
from apps.sync.services import process_batch, visible_change_events
from apps.sync.validation import validate_sync_protocol

MAX_SYNC_BATCH_BYTES = 1024 * 1024

SYNC_ERROR_RESPONSES = {
    400: ApiErrorSerializer,
    401: ApiErrorSerializer,
    403: ApiErrorSerializer,
    404: ApiErrorSerializer,
}


class SchoolSyncContextMixin:
    def get_school(self) -> School:
        if not hasattr(self, "_sync_school"):
            self._sync_school = get_object_or_404(School, pk=self.kwargs["school_id"])
        return self._sync_school

    def get_permission_school(self, request, obj=None) -> School:
        return self.get_school()


class SyncCommandBatchView(SchoolSyncContextMixin, APIView):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.VIEW_SCHOOL

    @extend_schema(
        operation_id="sync_command_batch",
        summary="Apply an idempotent batch of offline commands",
        tags=["Synchronization"],
        request=SyncCommandBatchSerializer,
        responses={200: SyncCommandBatchResponseSerializer, **SYNC_ERROR_RESPONSES},
    )
    def post(self, request, *args, **kwargs):
        serialized_size = len(
            json.dumps(request.data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        )
        if serialized_size > MAX_SYNC_BATCH_BYTES:
            raise serializers.ValidationError(
                {"operations": f"The serialized batch cannot exceed {MAX_SYNC_BATCH_BYTES} bytes."}
            )

        validate_sync_protocol(request.data)
        serializer = SyncCommandBatchSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        results = process_batch(
            user=request.user,
            school=self.get_school(),
            operations=serializer.validated_data["operations"],
        )
        response = SyncCommandBatchResponseSerializer({"results": results})
        return Response(response.data, status=status.HTTP_200_OK)


class ChangeFeedView(SchoolSyncContextMixin, APIView):
    permission_classes = (IsAuthenticated, HasSchoolAction)
    required_school_action = SchoolAction.VIEW_SCHOOL

    @extend_schema(
        operation_id="sync_change_feed",
        summary="Read visible server changes after a cursor",
        tags=["Synchronization"],
        parameters=[ChangeFeedQuerySerializer],
        responses={200: ChangeFeedResponseSerializer, **SYNC_ERROR_RESPONSES},
    )
    def get(self, request, *args, **kwargs):
        query = ChangeFeedQuerySerializer(data=request.query_params)
        query.is_valid(raise_exception=True)
        cursor = query.validated_data["cursor"]
        limit = query.validated_data["limit"]

        page = list(
            visible_change_events(user=request.user, school=self.get_school())
            .filter(sequence__gt=cursor)
            .order_by("sequence")[: limit + 1]
        )
        has_more = len(page) > limit
        changes = page[:limit]
        next_cursor = changes[-1].sequence if changes else cursor
        data = {
            "changes": ChangeEventSerializer(changes, many=True).data,
            "nextCursor": str(next_cursor),
            "hasMore": has_more,
        }
        return Response(data, status=status.HTTP_200_OK)
