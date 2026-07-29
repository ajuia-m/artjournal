from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel


class Subject(TimeStampedModel):
    school = models.ForeignKey(
        "schools.School",
        on_delete=models.CASCADE,
        related_name="subjects",
    )
    name = models.CharField(max_length=150)
    description = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["name", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["school", "name"],
                name="subject_unique_name_per_school",
            ),
        ]
        indexes = [
            models.Index(
                fields=["school", "is_active", "name"],
                name="subject_school_active_name_idx",
            ),
        ]

    def __str__(self) -> str:
        return self.name


class GroupSubject(TimeStampedModel):
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="subject_assignments",
    )
    subject = models.ForeignKey(
        Subject,
        on_delete=models.CASCADE,
        related_name="group_assignments",
    )

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["group", "subject"],
                name="group_subject_unique_pair",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.group_id
            and self.subject_id
            and self.group.academic_year.school_id != self.subject.school_id
        ):
            raise ValidationError({"subject": "Group and subject must belong to the same school."})

    def __str__(self) -> str:
        return f"{self.group}: {self.subject}"


class ScheduleEntry(TimeStampedModel):
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="schedule_entries",
    )
    subject = models.ForeignKey(
        Subject,
        on_delete=models.CASCADE,
        related_name="schedule_entries",
    )
    day_of_week = models.PositiveSmallIntegerField()
    start_time = models.TimeField(null=True, blank=True)
    end_time = models.TimeField(null=True, blank=True)

    class Meta:
        ordering = ["group", "day_of_week", "start_time", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["group", "subject", "day_of_week", "start_time"],
                name="schedule_entry_unique_slot",
                nulls_distinct=False,
            ),
            models.CheckConstraint(
                condition=Q(day_of_week__gte=1, day_of_week__lte=7),
                name="schedule_entry_day_valid",
            ),
            models.CheckConstraint(
                condition=Q(end_time__isnull=True)
                | (Q(start_time__isnull=False) & Q(end_time__gt=models.F("start_time"))),
                name="schedule_entry_times_ordered",
            ),
        ]
        indexes = [
            models.Index(
                fields=["group", "day_of_week"],
                name="schedule_entry_group_day_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.group_id
            and self.subject_id
            and self.group.academic_year.school_id != self.subject.school_id
        ):
            raise ValidationError({"subject": "Group and subject must belong to the same school."})

    def __str__(self) -> str:
        return f"{self.group}: {self.day_of_week} — {self.subject}"


class Topic(TimeStampedModel):
    subject = models.ForeignKey(
        Subject,
        on_delete=models.CASCADE,
        related_name="topics",
    )
    name = models.CharField(max_length=255)
    description = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["subject", "name", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["subject", "name"],
                name="topic_unique_name_per_subject",
            ),
        ]
        indexes = [
            models.Index(
                fields=["subject", "is_active", "name"],
                name="topic_subject_active_name_idx",
            ),
        ]

    def __str__(self) -> str:
        return self.name


class TopicCriterion(TimeStampedModel):
    topic = models.ForeignKey(
        Topic,
        on_delete=models.CASCADE,
        related_name="criteria",
    )
    name = models.CharField(max_length=255)
    max_points = models.PositiveIntegerField()
    position = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["topic", "position", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["topic", "name"],
                name="topic_criterion_unique_name",
            ),
            models.UniqueConstraint(
                fields=["topic", "position"],
                name="topic_criterion_unique_position",
            ),
            models.CheckConstraint(
                condition=Q(max_points__gt=0),
                name="topic_criterion_max_points_positive",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.topic}: {self.name}"


class TopicGroupAssignment(TimeStampedModel):
    topic = models.ForeignKey(
        Topic,
        on_delete=models.CASCADE,
        related_name="group_assignments",
    )
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="topic_assignments",
    )

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["topic", "group"],
                name="topic_group_assignment_unique_pair",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.topic_id
            and self.group_id
            and self.topic.subject.school_id != self.group.academic_year.school_id
        ):
            raise ValidationError({"group": "Topic and group must belong to the same school."})


class TopicPeriodAssignment(TimeStampedModel):
    topic = models.ForeignKey(
        Topic,
        on_delete=models.CASCADE,
        related_name="period_assignments",
    )
    academic_period = models.ForeignKey(
        "education.AcademicPeriod",
        on_delete=models.CASCADE,
        related_name="topic_assignments",
    )

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["topic", "academic_period"],
                name="topic_period_assignment_unique_pair",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.topic_id
            and self.academic_period_id
            and self.topic.subject.school_id != self.academic_period.academic_year.school_id
        ):
            raise ValidationError(
                {"academic_period": ("Topic and academic period must belong to the same school.")}
            )
