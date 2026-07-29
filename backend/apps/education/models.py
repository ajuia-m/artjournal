from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel


class AcademicYear(TimeStampedModel):
    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="academic_years",
    )
    name = models.CharField(max_length=100)
    is_active = models.BooleanField(default=False)

    class Meta:
        ordering = ["-is_active", "name", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["school", "name"],
                name="academic_year_unique_name_per_school",
            ),
            models.UniqueConstraint(
                fields=["school"],
                condition=Q(is_active=True),
                name="academic_year_one_active_per_school",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "is_active"],
                name="acyear_school_active_idx",
            ),
        ]

    def __str__(self) -> str:
        return self.name


class AcademicPeriod(TimeStampedModel):
    class Kind(models.TextChoices):
        QUARTER = "quarter", "Quarter"
        SEMESTER = "semester", "Semester"
        YEAR = "year", "Year"
        CUSTOM = "custom", "Custom"

    academic_year = models.ForeignKey(
        AcademicYear,
        on_delete=models.CASCADE,
        related_name="periods",
    )
    name = models.CharField(max_length=100)
    kind = models.CharField(
        max_length=20,
        choices=Kind.choices,
        default=Kind.QUARTER,
    )
    start_date = models.DateField()
    end_date = models.DateField()
    position = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["academic_year", "start_date", "position", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["academic_year", "name"],
                name="academic_period_unique_name_per_year",
            ),
            models.CheckConstraint(
                condition=Q(end_date__gte=models.F("start_date")),
                name="academic_period_dates_ordered",
            ),
            models.CheckConstraint(
                condition=Q(kind__in=("quarter", "semester", "year", "custom")),
                name="academic_period_kind_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["academic_year", "start_date", "end_date"],
                name="academic_period_year_dates_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.academic_year}: {self.name}"


class Group(TimeStampedModel):
    academic_year = models.ForeignKey(
        AcademicYear,
        on_delete=models.CASCADE,
        related_name="groups",
    )
    name = models.CharField(max_length=150)
    notes = models.TextField(blank=True)

    class Meta:
        ordering = ["academic_year", "name", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["academic_year", "name"],
                name="education_group_unique_name_per_year",
            ),
        ]
        indexes = [
            models.Index(
                fields=["academic_year", "name"],
                name="education_group_year_name_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.name} ({self.academic_year})"


class Student(TimeStampedModel):
    class Status(models.TextChoices):
        ACTIVE = "active", "Active"
        ARCHIVED = "archived", "Archived"
        DELETED = "deleted", "Deleted"

    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="students",
    )
    last_name = models.CharField(max_length=150)
    first_name = models.CharField(max_length=150)
    birthday = models.DateField(null=True, blank=True)
    contract_number = models.CharField(max_length=100, blank=True)
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.ACTIVE,
    )
    archive_date = models.DateField(null=True, blank=True)
    archive_reason = models.TextField(blank=True)
    custom_fields = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ["last_name", "first_name", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(status__in=("active", "archived", "deleted")),
                name="student_status_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "status", "last_name"],
                name="student_school_status_name_idx",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.last_name} {self.first_name}".strip()


class Enrollment(TimeStampedModel):
    class Status(models.TextChoices):
        ACTIVE = "active", "Active"
        COMPLETED = "completed", "Completed"
        TRANSFERRED = "transferred", "Transferred"
        ARCHIVED = "archived", "Archived"

    student = models.ForeignKey(
        Student,
        on_delete=models.CASCADE,
        related_name="enrollments",
    )
    group = models.ForeignKey(
        Group,
        on_delete=models.CASCADE,
        related_name="enrollments",
    )
    started_on = models.DateField()
    ended_on = models.DateField(null=True, blank=True)
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.ACTIVE,
    )

    class Meta:
        ordering = ["student", "-started_on", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["student"],
                condition=Q(ended_on__isnull=True),
                name="enrollment_one_current_per_student",
            ),
            models.CheckConstraint(
                condition=Q(ended_on__isnull=True) | Q(ended_on__gte=models.F("started_on")),
                name="enrollment_dates_ordered",
            ),
            models.CheckConstraint(
                condition=Q(status__in=("active", "completed", "transferred", "archived")),
                name="enrollment_status_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["group", "ended_on"],
                name="enrollment_group_current_idx",
            ),
            models.Index(
                fields=["student", "started_on"],
                name="enrollment_student_start_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.student_id
            and self.group_id
            and self.student.school_id != self.group.academic_year.school_id
        ):
            raise ValidationError({"group": "Student and group must belong to the same school."})

    def __str__(self) -> str:
        return f"{self.student} → {self.group}"


class CalendarException(TimeStampedModel):
    class Kind(models.TextChoices):
        HOLIDAY = "holiday", "Holiday"
        CANCELLATION = "cancellation", "Cancellation"
        OTHER = "other", "Other"

    academic_year = models.ForeignKey(
        AcademicYear,
        on_delete=models.CASCADE,
        related_name="calendar_exceptions",
    )
    group = models.ForeignKey(
        Group,
        on_delete=models.CASCADE,
        related_name="calendar_exceptions",
        null=True,
        blank=True,
    )
    date = models.DateField()
    kind = models.CharField(
        max_length=20,
        choices=Kind.choices,
        default=Kind.HOLIDAY,
    )
    reason = models.CharField(max_length=255, blank=True)

    class Meta:
        ordering = ["date", "group", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["academic_year", "date", "kind"],
                condition=Q(group__isnull=True),
                name="calendar_exception_unique_school_scope",
            ),
            models.UniqueConstraint(
                fields=["academic_year", "group", "date", "kind"],
                condition=Q(group__isnull=False),
                name="calendar_exception_unique_group_scope",
            ),
            models.CheckConstraint(
                condition=Q(kind__in=("holiday", "cancellation", "other")),
                name="calendar_exception_kind_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["academic_year", "date"],
                name="calex_year_date_idx",
            ),
            models.Index(
                fields=["group", "date"],
                name="calex_group_date_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.group_id
            and self.academic_year_id
            and self.group.academic_year_id != self.academic_year_id
        ):
            raise ValidationError({"group": "Group must belong to the selected academic year."})

    def __str__(self) -> str:
        scope = self.group.name if self.group_id else self.academic_year.school.name
        return f"{self.date}: {scope}"
