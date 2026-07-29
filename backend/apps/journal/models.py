from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q

from apps.core.models import TimeStampedModel


class Lesson(TimeStampedModel):
    group = models.ForeignKey(
        "education.Group",
        on_delete=models.CASCADE,
        related_name="lessons",
    )
    subject = models.ForeignKey(
        "curriculum.Subject",
        on_delete=models.CASCADE,
        related_name="lessons",
    )
    date = models.DateField()
    start_time = models.TimeField(null=True, blank=True)
    end_time = models.TimeField(null=True, blank=True)
    custom_topic_name = models.CharField(max_length=255, blank=True)

    class Meta:
        ordering = ["date", "start_time", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(end_time__isnull=True)
                | (Q(start_time__isnull=False) & Q(end_time__gt=models.F("start_time"))),
                name="lesson_times_ordered",
            ),
        ]
        indexes = [
            models.Index(
                fields=["group", "date"],
                name="lesson_group_date_idx",
            ),
            models.Index(
                fields=["subject", "date"],
                name="lesson_subject_date_idx",
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
        return f"{self.date}: {self.group} — {self.subject}"


class LessonTopic(TimeStampedModel):
    lesson = models.ForeignKey(
        Lesson,
        on_delete=models.CASCADE,
        related_name="topic_links",
    )
    topic = models.ForeignKey(
        "curriculum.Topic",
        on_delete=models.CASCADE,
        related_name="lesson_links",
    )
    position = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["lesson", "position", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["lesson", "topic"],
                name="lesson_topic_unique_pair",
            ),
            models.UniqueConstraint(
                fields=["lesson", "position"],
                name="lesson_topic_unique_position",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if self.lesson_id and self.topic_id and self.lesson.subject_id != self.topic.subject_id:
            raise ValidationError({"topic": "Lesson and topic must use the same subject."})

    def __str__(self) -> str:
        return f"{self.lesson}: {self.topic}"


class StudentLessonState(TimeStampedModel):
    student = models.ForeignKey(
        "education.Student",
        on_delete=models.CASCADE,
        related_name="lesson_states",
    )
    lesson = models.ForeignKey(
        Lesson,
        on_delete=models.CASCADE,
        related_name="student_states",
    )
    grade = models.SmallIntegerField(null=True, blank=True)
    is_present = models.BooleanField(default=True)
    is_excused_absence = models.BooleanField(default=False)
    homework_points = models.SmallIntegerField(null=True, blank=True)
    comment = models.TextField(blank=True)
    note = models.TextField(blank=True)

    class Meta:
        ordering = ["lesson", "student", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["student", "lesson"],
                name="student_lesson_state_unique_pair",
            ),
            models.CheckConstraint(
                condition=Q(grade__isnull=True) | Q(grade__gte=0, grade__lte=5),
                name="student_lesson_grade_valid",
            ),
            models.CheckConstraint(
                condition=Q(homework_points__isnull=True)
                | Q(homework_points__gte=0, homework_points__lte=101),
                name="student_lesson_homework_valid",
            ),
            models.CheckConstraint(
                condition=Q(is_present=False) | Q(is_excused_absence=False),
                name="student_lesson_attendance_consistent",
            ),
        ]
        indexes = [
            models.Index(
                fields=["student", "lesson"],
                name="st_lesson_lookup_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.student_id
            and self.lesson_id
            and self.student.school_id != self.lesson.group.academic_year.school_id
        ):
            raise ValidationError({"student": "Student and lesson must belong to the same school."})


class StudentTopicProgress(TimeStampedModel):
    student = models.ForeignKey(
        "education.Student",
        on_delete=models.CASCADE,
        related_name="topic_progress",
    )
    topic = models.ForeignKey(
        "curriculum.Topic",
        on_delete=models.CASCADE,
        related_name="student_progress",
    )
    stage = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["topic", "student", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["student", "topic"],
                name="student_topic_progress_unique_pair",
            ),
            models.CheckConstraint(
                condition=Q(stage__gte=0, stage__lte=100),
                name="student_topic_progress_stage_valid",
            ),
        ]
        indexes = [
            models.Index(
                fields=["student", "topic"],
                name="st_topic_lookup_idx",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if (
            self.student_id
            and self.topic_id
            and self.student.school_id != self.topic.subject.school_id
        ):
            raise ValidationError({"student": "Student and topic must belong to the same school."})


class CriterionScore(TimeStampedModel):
    progress = models.ForeignKey(
        StudentTopicProgress,
        on_delete=models.CASCADE,
        related_name="criterion_scores",
    )
    criterion = models.ForeignKey(
        "curriculum.TopicCriterion",
        on_delete=models.CASCADE,
        related_name="student_scores",
    )
    score = models.PositiveIntegerField()

    class Meta:
        ordering = ["progress", "criterion__position", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=["progress", "criterion"],
                name="criterion_score_unique_pair",
            ),
            models.CheckConstraint(
                condition=Q(score__gte=0),
                name="criterion_score_non_negative",
            ),
        ]

    def clean(self) -> None:
        super().clean()
        if self.progress_id and self.criterion_id:
            if self.progress.topic_id != self.criterion.topic_id:
                raise ValidationError({"criterion": "Criterion must belong to the progress topic."})
            if self.score > self.criterion.max_points:
                raise ValidationError({"score": "Score cannot exceed the criterion maximum."})
