import pytest
from django.db import IntegrityError, transaction

from apps.schools.models import School


@pytest.mark.django_db
def test_school_slug_is_unique() -> None:
    School.objects.create(
        name="First school",
        slug="art-school",
        default_currency="RUB",
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            School.objects.create(
                name="Second school",
                slug="art-school",
                default_currency="USD",
            )


@pytest.mark.django_db
def test_school_currency_is_validated_by_database() -> None:
    with pytest.raises(IntegrityError):
        with transaction.atomic():
            School.objects.create(
                name="Broken school",
                slug="broken-school",
                default_currency="rub",
            )
