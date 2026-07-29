from django.core.validators import RegexValidator
from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel

currency_validator = RegexValidator(
    regex=r"^[A-Z]{3}$",
    message="Currency must be a three-letter uppercase ISO 4217 code.",
)


class School(TimeStampedModel):
    name = models.CharField(max_length=255)
    slug = models.SlugField(max_length=100, unique=True)
    default_currency = models.CharField(
        max_length=3,
        validators=[currency_validator],
    )

    class Meta:
        ordering = ["name", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(default_currency__regex=r"^[A-Z]{3}$"),
                name="school_currency_three_uppercase_letters",
            ),
        ]

    def __str__(self) -> str:
        return self.name
