from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework import serializers

from apps.accounts.models import User
from apps.curriculum.models import Subject
from apps.education.models import Group
from apps.schools.models import Membership, School, TeachingAssignment
from apps.schools.services import (
    LastAdministratorError,
    create_membership,
    update_membership,
)


def _raise_drf_validation_error(error: DjangoValidationError) -> None:
    if hasattr(error, "message_dict"):
        raise serializers.ValidationError(error.message_dict) from error
    raise serializers.ValidationError({"detail": error.messages}) from error


class SchoolSerializer(serializers.ModelSerializer):
    class Meta:
        model = School
        fields = ("id", "name", "slug", "default_currency")


class MembershipSerializer(serializers.ModelSerializer):
    user_id = serializers.PrimaryKeyRelatedField(
        source="user",
        queryset=User.objects.all(),
    )
    username = serializers.CharField(source="user.username", read_only=True)
    email = serializers.EmailField(source="user.email", read_only=True)
    school_id = serializers.UUIDField(read_only=True)

    class Meta:
        model = Membership
        fields = (
            "id",
            "school_id",
            "user_id",
            "username",
            "email",
            "role",
            "is_active",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "created_at", "updated_at")

    def create(self, validated_data):
        try:
            return create_membership(
                school=self.context["school"],
                **validated_data,
            )
        except DjangoValidationError as error:
            _raise_drf_validation_error(error)

    def update(self, instance, validated_data):
        if "user" in validated_data:
            raise serializers.ValidationError({"user_id": "Membership user cannot be changed."})
        try:
            return update_membership(
                instance.pk,
                role=validated_data.get("role", instance.role),
                is_active=validated_data.get("is_active", instance.is_active),
            )
        except LastAdministratorError as error:
            _raise_drf_validation_error(error)
        except DjangoValidationError as error:
            _raise_drf_validation_error(error)


class TeachingAssignmentSerializer(serializers.ModelSerializer):
    membership_id = serializers.PrimaryKeyRelatedField(
        source="membership",
        queryset=Membership.objects.all(),
    )
    group_id = serializers.PrimaryKeyRelatedField(
        source="group",
        queryset=Group.objects.all(),
    )
    subject_id = serializers.PrimaryKeyRelatedField(
        source="subject",
        queryset=Subject.objects.all(),
        allow_null=True,
        required=False,
    )
    username = serializers.CharField(source="membership.user.username", read_only=True)
    group_name = serializers.CharField(source="group.name", read_only=True)
    subject_name = serializers.CharField(
        source="subject.name",
        read_only=True,
        allow_null=True,
    )

    class Meta:
        model = TeachingAssignment
        fields = (
            "id",
            "membership_id",
            "username",
            "group_id",
            "group_name",
            "subject_id",
            "subject_name",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "created_at", "updated_at")

    def validate(self, attrs):
        instance = self.instance
        assignment = TeachingAssignment(
            membership=attrs.get("membership", getattr(instance, "membership", None)),
            group=attrs.get("group", getattr(instance, "group", None)),
            subject=attrs.get("subject", getattr(instance, "subject", None)),
        )
        if instance is not None:
            assignment.pk = instance.pk
            assignment._state.adding = False
        school = self.context["school"]
        if assignment.membership_id and assignment.membership.school_id != school.id:
            raise serializers.ValidationError(
                {"membership_id": "Membership does not belong to this school."}
            )
        try:
            assignment.full_clean()
        except DjangoValidationError as error:
            _raise_drf_validation_error(error)
        return attrs
