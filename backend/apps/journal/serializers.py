from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import transaction
from django.db.models import Q
from rest_framework import serializers

from apps.curriculum.models import GroupSubject, Subject, Topic, TopicGroupAssignment
from apps.education.models import Enrollment, Student
from apps.journal.models import Lesson, LessonTopic, StudentLessonState
from apps.journal.services import (
    create_student_lesson_state,
    update_student_lesson_state,
)


def _raise_drf_validation_error(error: DjangoValidationError) -> None:
    if hasattr(error, "message_dict"):
        raise serializers.ValidationError(error.message_dict) from error
    raise serializers.ValidationError({"detail": error.messages}) from error


class LessonFilterSerializer(serializers.Serializer):
    date_from = serializers.DateField(required=False)
    date_to = serializers.DateField(required=False)
    subject_id = serializers.UUIDField(required=False)

    def validate(self, attrs):
        date_from = attrs.get("date_from")
        date_to = attrs.get("date_to")
        if date_from and date_to and date_to < date_from:
            raise serializers.ValidationError({"date_to": "date_to must be on or after date_from."})
        return attrs


class LessonTopicSerializer(serializers.ModelSerializer):
    topic_id = serializers.UUIDField(source="topic.id", read_only=True)
    topic_name = serializers.CharField(source="topic.name", read_only=True)

    class Meta:
        model = LessonTopic
        fields = ("topic_id", "topic_name", "position")


class LessonSerializer(serializers.ModelSerializer):
    group_id = serializers.UUIDField(read_only=True)
    subject_id = serializers.PrimaryKeyRelatedField(
        source="subject",
        queryset=Subject.objects.all(),
    )
    subject_name = serializers.CharField(source="subject.name", read_only=True)
    topic_ids = serializers.PrimaryKeyRelatedField(
        source="topics_input",
        queryset=Topic.objects.all(),
        many=True,
        required=False,
        write_only=True,
    )
    topics = LessonTopicSerializer(source="topic_links", many=True, read_only=True)

    class Meta:
        model = Lesson
        fields = (
            "id",
            "group_id",
            "subject_id",
            "subject_name",
            "date",
            "start_time",
            "end_time",
            "custom_topic_name",
            "topic_ids",
            "topics",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "created_at", "updated_at")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        group = self.context.get("group")
        if group is not None:
            school = group.academic_year.school
            self.fields["subject_id"].queryset = Subject.objects.filter(school=school)
            self.fields["topic_ids"].child_relation.queryset = Topic.objects.filter(
                subject__school=school
            )

    def validate(self, attrs):
        group = self.context["group"]
        instance = self.instance
        subject = attrs.get("subject", getattr(instance, "subject", None))

        if (
            subject is not None
            and not GroupSubject.objects.filter(
                group=group,
                subject=subject,
            ).exists()
        ):
            raise serializers.ValidationError(
                {"subject_id": "Subject must be assigned to this group."}
            )

        topics_supplied = "topics_input" in attrs
        topics = attrs.get("topics_input", [])
        if not topics_supplied and instance is not None:
            topics = [link.topic for link in instance.topic_links.select_related("topic")]

        topic_ids = [topic.id for topic in topics]
        if len(topic_ids) != len(set(topic_ids)):
            raise serializers.ValidationError({"topic_ids": "Topics must be unique."})
        if subject is not None and any(topic.subject_id != subject.id for topic in topics):
            raise serializers.ValidationError(
                {"topic_ids": "Every topic must belong to the lesson subject."}
            )
        assigned_topic_ids = set(
            TopicGroupAssignment.objects.filter(
                group=group,
                topic_id__in=topic_ids,
            ).values_list("topic_id", flat=True)
        )
        if assigned_topic_ids != set(topic_ids):
            raise serializers.ValidationError(
                {"topic_ids": "Every topic must be assigned to this group."}
            )

        candidate = Lesson(
            id=getattr(instance, "id", None),
            group=group,
            subject=subject,
            date=attrs.get("date", getattr(instance, "date", None)),
            start_time=attrs.get("start_time", getattr(instance, "start_time", None)),
            end_time=attrs.get("end_time", getattr(instance, "end_time", None)),
            custom_topic_name=attrs.get(
                "custom_topic_name",
                getattr(instance, "custom_topic_name", ""),
            ),
        )
        if instance is not None:
            candidate._state.adding = False
        try:
            candidate.full_clean()
        except DjangoValidationError as error:
            _raise_drf_validation_error(error)
        return attrs

    @staticmethod
    def _replace_topics(lesson: Lesson, topics: list[Topic]) -> None:
        lesson.topic_links.all().delete()
        LessonTopic.objects.bulk_create(
            [
                LessonTopic(lesson=lesson, topic=topic, position=position)
                for position, topic in enumerate(topics)
            ]
        )

    @transaction.atomic
    def create(self, validated_data):
        topics = validated_data.pop("topics_input", [])
        lesson = Lesson.objects.create(group=self.context["group"], **validated_data)
        self._replace_topics(lesson, topics)
        return lesson

    @transaction.atomic
    def update(self, instance, validated_data):
        topics = validated_data.pop("topics_input", None)
        for field, value in validated_data.items():
            setattr(instance, field, value)
        instance.save()
        if topics is not None:
            self._replace_topics(instance, topics)
        return instance


class StudentLessonStateSerializer(serializers.ModelSerializer):
    lesson_id = serializers.UUIDField(read_only=True)
    student_id = serializers.PrimaryKeyRelatedField(
        source="student",
        queryset=Student.objects.all(),
    )
    student_name = serializers.SerializerMethodField()

    class Meta:
        model = StudentLessonState
        fields = (
            "id",
            "lesson_id",
            "student_id",
            "student_name",
            "grade",
            "is_present",
            "is_excused_absence",
            "homework_points",
            "comment",
            "note",
            "version",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "version", "created_at", "updated_at")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        lesson = self.context.get("lesson")
        if lesson is not None:
            self.fields["student_id"].queryset = Student.objects.filter(
                school=lesson.group.academic_year.school
            )

    def get_student_name(self, state: StudentLessonState) -> str:
        return f"{state.student.last_name} {state.student.first_name}".strip()

    def validate(self, attrs):
        lesson = self.context["lesson"]
        instance = self.instance
        student = attrs.get("student", getattr(instance, "student", None))

        if instance is not None and "student" in attrs and student.id != instance.student_id:
            raise serializers.ValidationError(
                {"student_id": "The student of an existing state cannot be changed."}
            )
        if (
            student is not None
            and not Enrollment.objects.filter(
                student=student,
                group=lesson.group,
                started_on__lte=lesson.date,
            )
            .filter(Q(ended_on__isnull=True) | Q(ended_on__gte=lesson.date))
            .exists()
        ):
            raise serializers.ValidationError(
                {"student_id": "Student was not enrolled in this group on the lesson date."}
            )

        candidate = StudentLessonState(
            id=getattr(instance, "id", None),
            lesson=lesson,
            student=student,
            grade=attrs.get("grade", getattr(instance, "grade", None)),
            is_present=attrs.get("is_present", getattr(instance, "is_present", True)),
            is_excused_absence=attrs.get(
                "is_excused_absence",
                getattr(instance, "is_excused_absence", False),
            ),
            homework_points=attrs.get(
                "homework_points",
                getattr(instance, "homework_points", None),
            ),
            comment=attrs.get("comment", getattr(instance, "comment", "")),
            note=attrs.get("note", getattr(instance, "note", "")),
        )
        if instance is not None:
            candidate._state.adding = False
        try:
            candidate.full_clean()
        except DjangoValidationError as error:
            _raise_drf_validation_error(error)
        return attrs

    def create(self, validated_data):
        request = self.context.get("request")
        return create_student_lesson_state(
            lesson=self.context["lesson"],
            validated_data=validated_data,
            actor=self.context.get("actor", getattr(request, "user", None)),
            entity_id=self.context.get("entity_id"),
        )

    def update(self, instance, validated_data):
        request = self.context.get("request")
        return update_student_lesson_state(
            instance=instance,
            validated_data=validated_data,
            actor=self.context.get("actor", getattr(request, "user", None)),
            expected_version=self.context.get("expected_version"),
        )
