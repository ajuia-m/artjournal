import json
import os

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.accounts.models import User
from apps.schools.models import Membership, School

E2E_USERNAME = "android-e2e-teacher"
E2E_PASSWORD = "AndroidE2E-password-2026"
E2E_SCHOOL_SLUG = "android-e2e-school"


class Command(BaseCommand):
    help = "Create deterministic Android/backend E2E data (explicit opt-in only)."

    @transaction.atomic
    def handle(self, *args, **options):
        if os.getenv("ARTJOURNAL_ALLOW_E2E_SEED", "").lower() != "true":
            raise CommandError(
                "Refusing to create E2E data. Set ARTJOURNAL_ALLOW_E2E_SEED=true explicitly."
            )

        user, _ = User.objects.get_or_create(username=E2E_USERNAME)
        user.first_name = "Android E2E"
        user.last_name = "Teacher"
        user.email = "android-e2e@example.invalid"
        user.is_active = True
        user.set_password(E2E_PASSWORD)
        user.save()

        school, _ = School.objects.update_or_create(
            slug=E2E_SCHOOL_SLUG,
            defaults={
                "name": "Art Journal E2E School",
                "default_currency": "RUB",
            },
        )
        Membership.objects.update_or_create(
            user=user,
            school=school,
            defaults={"role": Membership.Role.TEACHER, "is_active": True},
        )

        self.stdout.write(
            json.dumps(
                {
                    "username": user.username,
                    "schoolId": str(school.id),
                    "schoolSlug": school.slug,
                    "role": Membership.Role.TEACHER,
                },
                sort_keys=True,
            )
        )
