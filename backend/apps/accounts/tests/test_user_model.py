from uuid import UUID

import pytest

from apps.accounts.models import User


@pytest.mark.django_db
def test_user_uses_uuid_primary_key() -> None:
    user = User.objects.create_user(
        username="teacher",
        password="test-password",
    )

    assert isinstance(user.id, UUID)
