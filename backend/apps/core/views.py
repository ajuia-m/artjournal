from django.db import OperationalError, connection
from drf_spectacular.utils import extend_schema
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from apps.core.serializers import HealthResponseSerializer


@extend_schema(
    operation_id="system_health",
    summary="Check application and database readiness",
    tags=["System"],
    auth=[],
    responses={
        200: HealthResponseSerializer,
        503: HealthResponseSerializer,
    },
)
@api_view(["GET"])
@permission_classes([AllowAny])
def health(_request) -> Response:
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except OperationalError:
        return Response(
            {"status": "unavailable", "database": "error"},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )

    return Response({"status": "ok", "database": "ok"})
