import pytest
from django.core.cache import cache
from rest_framework.test import APIClient

from apps.accounts.models import User
from apps.schools.models import Membership, School


@pytest.fixture(autouse=True)
def clear_throttle_cache():
    cache.clear()
    yield
    cache.clear()


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.fixture
def auth_user() -> User:
    return User.objects.create_user(
        username="authenticated-teacher",
        email="teacher@example.com",
        password="strong-test-password",
    )


@pytest.mark.django_db
def test_login_and_current_user_include_active_memberships(
    api_client: APIClient,
    auth_user: User,
) -> None:
    school = School.objects.create(
        name="Central Art School",
        slug="central-art-school",
        default_currency="RUB",
    )
    Membership.objects.create(
        user=auth_user,
        school=school,
        role=Membership.Role.TEACHER,
    )
    inactive_school = School.objects.create(
        name="Inactive Art School",
        slug="inactive-art-school",
        default_currency="RUB",
    )
    Membership.objects.create(
        user=auth_user,
        school=inactive_school,
        role=Membership.Role.TEACHER,
        is_active=False,
    )

    login = api_client.post(
        "/api/v1/auth/token/",
        {
            "username": auth_user.username,
            "password": "strong-test-password",
        },
        format="json",
    )

    assert login.status_code == 200
    assert {"access", "refresh"} <= login.json().keys()
    from rest_framework_simplejwt.tokens import AccessToken

    token = AccessToken(login.json()["access"])
    assert token["user_id"] == str(auth_user.id)
    assert "role" not in token
    assert "school_id" not in token
    api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {login.json()['access']}")

    current_user = api_client.get("/api/v1/auth/me/")

    assert current_user.status_code == 200
    assert current_user.json()["id"] == str(auth_user.id)
    assert current_user.json()["memberships"] == [
        {
            "id": str(auth_user.school_memberships.get(school=school).id),
            "school_id": str(school.id),
            "school_name": school.name,
            "school_slug": school.slug,
            "role": Membership.Role.TEACHER,
            "teaching_assignments": [],
        }
    ]


@pytest.mark.django_db
def test_refresh_rotation_and_logout_blacklist(
    api_client: APIClient,
    auth_user: User,
) -> None:
    login = api_client.post(
        "/api/v1/auth/token/",
        {
            "username": auth_user.username,
            "password": "strong-test-password",
        },
        format="json",
    )
    refresh = login.json()["refresh"]

    rotated = api_client.post(
        "/api/v1/auth/token/refresh/",
        {"refresh": refresh},
        format="json",
    )

    assert rotated.status_code == 200
    assert {"access", "refresh"} <= rotated.json().keys()

    logout = api_client.post(
        "/api/v1/auth/token/logout/",
        {"refresh": rotated.json()["refresh"]},
        format="json",
    )
    assert logout.status_code == 200

    rejected = api_client.post(
        "/api/v1/auth/token/refresh/",
        {"refresh": rotated.json()["refresh"]},
        format="json",
    )
    assert rejected.status_code == 401


@pytest.mark.django_db
def test_invalid_credentials_and_inactive_user_are_rejected(
    api_client: APIClient,
    auth_user: User,
) -> None:
    invalid_password = api_client.post(
        "/api/v1/auth/token/",
        {
            "username": auth_user.username,
            "password": "wrong-password",
        },
        format="json",
    )
    assert invalid_password.status_code == 401

    auth_user.is_active = False
    auth_user.save(update_fields=["is_active"])
    inactive_user = api_client.post(
        "/api/v1/auth/token/",
        {
            "username": auth_user.username,
            "password": "strong-test-password",
        },
        format="json",
    )
    assert inactive_user.status_code == 401


@pytest.mark.django_db
def test_current_user_requires_access_token(api_client: APIClient) -> None:
    assert api_client.get("/api/v1/auth/me/").status_code == 401
