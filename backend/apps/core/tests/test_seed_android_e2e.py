import pytest
from django.core.management import call_command
from django.core.management.base import CommandError

from apps.accounts.models import User
from apps.core.management.commands.seed_android_e2e import (
    E2E_PASSWORD,
    E2E_SCHOOL_SLUG,
    E2E_USERNAME,
)
from apps.schools.models import Membership, School


@pytest.mark.django_db
def test_seed_requires_explicit_opt_in(monkeypatch):
    monkeypatch.delenv("ARTJOURNAL_ALLOW_E2E_SEED", raising=False)

    with pytest.raises(CommandError, match="Refusing to create E2E data"):
        call_command("seed_android_e2e")

    assert not User.objects.filter(username=E2E_USERNAME).exists()


@pytest.mark.django_db
def test_seed_is_idempotent(monkeypatch):
    monkeypatch.setenv("ARTJOURNAL_ALLOW_E2E_SEED", "true")

    call_command("seed_android_e2e")
    call_command("seed_android_e2e")

    user = User.objects.get(username=E2E_USERNAME)
    school = School.objects.get(slug=E2E_SCHOOL_SLUG)
    membership = Membership.objects.get(user=user, school=school)
    assert user.check_password(E2E_PASSWORD)
    assert user.first_name == "Android E2E"
    assert school.name == "Art Journal E2E School"
    assert membership.role == Membership.Role.TEACHER
    assert membership.is_active is True
    assert User.objects.filter(username=E2E_USERNAME).count() == 1
    assert School.objects.filter(slug=E2E_SCHOOL_SLUG).count() == 1
