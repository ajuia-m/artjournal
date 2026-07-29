from django.core.exceptions import ValidationError
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


class Membership(TimeStampedModel):
    class Role(models.TextChoices):
        ADMIN = "admin", "Administrator"
        TEACHER = "teacher", "Teacher"

    user = models.ForeignKey(
        "accounts.User",
        on_delete=models.PROTECT,
        related_name="school_memberships",
    )
    school = models.ForeignKey(
        School,
        on_delete=models.CASCADE,
        related_name="memberships",
    )
    role = models.CharField(
        max_length=20,
        choices=Role.choices,
    )
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["school", "role", "user", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["user", "school"],
                name="school_membership_unique_user_school",
            ),
            models.CheckConstraint(
                condition=Q(role__in=("admin", "teacher")),
                name="school_membership_role_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "role", "is_active"],
                name="membership_school_role_idx",
            ),
            models.Index(
                fields=["user", "is_active"],
                name="membership_user_active_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if self.pk and self.role != self.Role.TEACHER and self.teaching_assignments.exists():
            raise ValidationError(
                {"role": "Remove teaching assignments before changing this role."}
            )

    def __str__(self) -> str:
        return f"{self.user} — {self.school} ({self.role})"


class TeachingAssignment(TimeStampedModel):
    membership = models.ForeignKey(
        Membership,
        on_delete=models.CASCADE,
        related_name="teaching_assignments",
    )
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="teaching_assignments",
    )
    subject = models.ForeignKey(
        "curriculum.Subject",
        on_delete=models.CASCADE,
        related_name="teaching_assignments",
        null=True,
        blank=True,
    )

    class Meta:
        ordering = ["membership", "group", "subject", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["membership", "group", "subject"],
                name="teaching_assignment_unique_scope",
                nulls_distinct=False,
            ),
        ]
        indexes = [
            models.Index(
                fields=["membership", "group"],
                name="teaching_member_group_idx",
            ),
            models.Index(
                fields=["group", "subject"],
                name="teaching_group_subject_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if not self.membership_id or not self.group_id:
            return
        if self.membership.role != Membership.Role.TEACHER:
            raise ValidationError(
                {"membership": "Teaching assignments require a teacher membership."}
            )
        group_school_id = self.group.academic_year.school_id
        if self.membership.school_id != group_school_id:
            raise ValidationError({"group": "Membership and group must belong to the same school."})
        if self.subject_id:
            if self.subject.school_id != group_school_id:
                raise ValidationError(
                    {"subject": "Subject and group must belong to the same school."}
                )
            if not self.group.subject_assignments.filter(subject=self.subject).exists():
                raise ValidationError(
                    {"subject": "Subject must be assigned to the selected group."}
                )
        overlapping_assignments = TeachingAssignment.objects.filter(
            membership=self.membership,
            group=self.group,
        )
        if self.pk:
            overlapping_assignments = overlapping_assignments.exclude(pk=self.pk)
        if self.subject_id:
            overlapping_assignments = overlapping_assignments.filter(subject__isnull=True)
        if overlapping_assignments.exists():
            raise ValidationError(
                {
                    "subject": (
                        "A group-wide assignment cannot be combined with "
                        "subject-specific assignments."
                    )
                }
            )

    def __str__(self) -> str:
        subject = self.subject.name if self.subject_id else "all subjects"
        return f"{self.membership}: {self.group} — {subject}"
