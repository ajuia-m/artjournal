from datetime import date
from decimal import Decimal

import pytest
from django.db import IntegrityError, transaction

from apps.education.models import Student
from apps.schools.models import School
from apps.tuition.models import Payment


@pytest.mark.django_db
def test_payment_amount_cannot_be_negative() -> None:
    school = School.objects.create(
        name="Art school",
        slug="art-school",
        default_currency="RUB",
    )
    student = Student.objects.create(
        school=school,
        last_name="Example",
        first_name="Student",
    )

    with pytest.raises(IntegrityError):
        with transaction.atomic():
            Payment.objects.create(
                student=student,
                date=date(2026, 9, 5),
                amount=Decimal("-1.00"),
                currency="RUB",
            )
