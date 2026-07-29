from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from apps.accounts.models import User
from apps.schools.models import Membership, TeachingAssignment


class TeachingAssignmentSerializer(serializers.ModelSerializer):
    group_id = serializers.UUIDField(read_only=True)
    group_name = serializers.CharField(source="group.name", read_only=True)
    subject_id = serializers.UUIDField(read_only=True, allow_null=True)
    subject_name = serializers.CharField(
        source="subject.name",
        read_only=True,
        allow_null=True,
    )

    class Meta:
        model = TeachingAssignment
        fields = (
            "id",
            "group_id",
            "group_name",
            "subject_id",
            "subject_name",
        )


class MembershipSerializer(serializers.ModelSerializer):
    school_id = serializers.UUIDField(read_only=True)
    school_name = serializers.CharField(source="school.name", read_only=True)
    school_slug = serializers.CharField(source="school.slug", read_only=True)
    teaching_assignments = TeachingAssignmentSerializer(many=True, read_only=True)

    class Meta:
        model = Membership
        fields = (
            "id",
            "school_id",
            "school_name",
            "school_slug",
            "role",
            "teaching_assignments",
        )


class CurrentUserSerializer(serializers.ModelSerializer):
    memberships = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = (
            "id",
            "username",
            "email",
            "first_name",
            "last_name",
            "memberships",
        )

    @extend_schema_field(MembershipSerializer(many=True))
    def get_memberships(self, user: User) -> list[dict[str, object]]:
        memberships = getattr(user, "active_school_memberships", ())
        return MembershipSerializer(memberships, many=True).data
