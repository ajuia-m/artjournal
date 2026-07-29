from django.core.validators import RegexValidator
from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel

currency_validator = RegexValidator(
    regex=r"^[A-Z]{3}$",
    message="Currency must be a three-letter uppercase ISO 4217 code.",
)


class Payment(TimeStampedModel):
    class Kind(models.TextChoices):
        TUITION = "tuition", "Tuition"
        MATERIALS = "materials", "Materials"

    student = models.ForeignKey(
        "education.Student",
        on_delete=models.CASCADE,
        related_name="payments",
    )
    date = models.DateField()
    amount = models.DecimalField(max_digits=12, decimal_places=2)
    currency = models.CharField(
        max_length=3,
        validators=[currency_validator],
    )
    kind = models.CharField(
        max_length=20,
        choices=Kind.choices,
        default=Kind.TUITION,
    )
    comment = models.TextField(blank=True)

    class Meta:
        ordering = ["-date", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(amount__gte=0),
                name="payment_amount_non_negative",
            ),
            models.CheckConstraint(
                condition=Q(currency__regex=r"^[A-Z]{3}$"),
                name="payment_currency_three_uppercase_letters",
            ),
            models.CheckConstraint(
                condition=Q(kind__in=("tuition", "materials")),
                name="payment_kind_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["student", "date"],
                name="payment_student_date_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.student}: {self.amount} {self.currency}"
