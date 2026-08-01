from typing import Any

from rest_framework.response import Response
from rest_framework.views import exception_handler

ERROR_CODES = {
    400: "validation_error",
    401: "authentication_required",
    403: "permission_denied",
    404: "not_found",
    405: "method_not_allowed",
    429: "throttled",
}

ERROR_MESSAGES = {
    400: "Request validation failed.",
    401: "Authentication credentials were not provided or are invalid.",
    403: "You do not have permission to perform this action.",
    404: "The requested resource was not found.",
    405: "This HTTP method is not allowed.",
    429: "Too many requests.",
}


def _detail_message(data: Any, fallback: str) -> str:
    if isinstance(data, dict) and set(data) == {"detail"}:
        return str(data["detail"])
    return fallback


def artjournal_exception_handler(exc: Exception, context: dict[str, Any]) -> Response | None:
    response = exception_handler(exc, context)
    if response is None:
        return None

    status_code = response.status_code
    original_data = response.data
    is_field_error = isinstance(original_data, (dict, list)) and not (
        isinstance(original_data, dict) and set(original_data) == {"detail"}
    )
    response.data = {
        "error": {
            "code": ERROR_CODES.get(status_code, "api_error"),
            "message": _detail_message(
                original_data,
                ERROR_MESSAGES.get(status_code, "The API request failed."),
            ),
            "fields": original_data if is_field_error else {},
        }
    }
    return response
