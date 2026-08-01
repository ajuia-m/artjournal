import pytest
from django.urls import reverse
from drf_spectacular.generators import SchemaGenerator
from rest_framework.test import APIClient

EXPECTED_PATHS = {
    "/api/v1/auth/me/",
    "/api/v1/auth/token/",
    "/api/v1/auth/token/logout/",
    "/api/v1/auth/token/refresh/",
    "/api/v1/health/",
    "/api/v1/schools/{school_id}/groups/{group_id}/lessons/",
    "/api/v1/schools/{school_id}/groups/{group_id}/lessons/{lesson_id}/",
    "/api/v1/schools/{school_id}/groups/{group_id}/lessons/{lesson_id}/states/",
    ("/api/v1/schools/{school_id}/groups/{group_id}/lessons/{lesson_id}/states/{state_id}/"),
    "/api/v1/schools/",
    "/api/v1/schools/{school_id}/",
    "/api/v1/schools/{school_id}/memberships/",
    "/api/v1/schools/{school_id}/memberships/{membership_id}/",
    "/api/v1/schools/{school_id}/teaching-assignments/",
    "/api/v1/schools/{school_id}/teaching-assignments/{assignment_id}/",
}


@pytest.mark.django_db
def test_openapi_schema_is_public_and_complete() -> None:
    schema = SchemaGenerator().get_schema(request=None, public=True)

    assert schema["openapi"] == "3.0.3"
    assert schema["info"]["title"] == "Art Journal API"
    assert set(schema["paths"]) == EXPECTED_PATHS
    assert schema["components"]["securitySchemes"]["jwtAuth"] == {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
    }
    assert "ApiError" in schema["components"]["schemas"]
    lesson_create = schema["paths"]["/api/v1/schools/{school_id}/groups/{group_id}/lessons/"][
        "post"
    ]
    assert lesson_create["responses"]["400"]["content"]["application/json"]["schema"] == {
        "$ref": "#/components/schemas/ApiError"
    }
    assert schema["paths"]["/api/v1/health/"]["get"].get("security", []) == []
    assert schema["paths"]["/api/v1/auth/me/"]["get"]["security"] == [{"jwtAuth": []}]

    operation_ids = [
        operation["operationId"]
        for path_item in schema["paths"].values()
        for method, operation in path_item.items()
        if method in {"get", "post", "put", "patch", "delete"}
    ]
    assert len(operation_ids) == len(set(operation_ids))


@pytest.mark.django_db
def test_schema_and_swagger_ui_do_not_require_authentication() -> None:
    client = APIClient()

    schema_response = client.get(f"{reverse('api-schema')}?format=json")
    docs_response = client.get(reverse("api-docs"))

    assert schema_response.status_code == 200
    assert schema_response.json()["openapi"] == "3.0.3"
    assert docs_response.status_code == 200
