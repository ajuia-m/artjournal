from unittest.mock import patch

import pytest
from django.db import OperationalError
from django.urls import reverse
from rest_framework.test import APIClient


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.mark.django_db
def test_health_reports_ready_database(api_client: APIClient) -> None:
    response = api_client.get(reverse("health"))

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


@pytest.mark.django_db
@patch("apps.core.views.connection.cursor", side_effect=OperationalError)
def test_health_reports_unavailable_database(
    _cursor,
    api_client: APIClient,
) -> None:
    response = api_client.get(reverse("health"))

    assert response.status_code == 503
    assert response.json() == {
        "status": "unavailable",
        "database": "error",
    }
