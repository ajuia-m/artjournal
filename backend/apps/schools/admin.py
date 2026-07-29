from django import forms
from django.contrib import admin

from apps.schools.models import Membership, School, TeachingAssignment
from apps.schools.services import update_membership


@admin.register(School)
class SchoolAdmin(admin.ModelAdmin):
    list_display = ("name", "slug", "default_currency")
    search_fields = ("name", "slug")


class TeachingAssignmentInline(admin.TabularInline):
    model = TeachingAssignment
    extra = 0


class MembershipAdminForm(forms.ModelForm):
    class Meta:
        model = Membership
        fields = ("user", "school", "role", "is_active")

    def clean(self):
        cleaned_data = super().clean()
        membership = self.instance
        role = cleaned_data.get("role")
        is_active = cleaned_data.get("is_active")
        if (
            membership.pk
            and membership.role == Membership.Role.ADMIN
            and membership.is_active
            and (role != Membership.Role.ADMIN or not is_active)
            and not Membership.objects.filter(
                school=membership.school,
                role=Membership.Role.ADMIN,
                is_active=True,
                user__is_active=True,
            )
            .exclude(pk=membership.pk)
            .exists()
        ):
            raise forms.ValidationError("A school must retain at least one active administrator.")
        if (
            membership.pk
            and role != Membership.Role.TEACHER
            and membership.teaching_assignments.exists()
        ):
            raise forms.ValidationError("Remove teaching assignments before changing this role.")
        return cleaned_data


@admin.register(Membership)
class MembershipAdmin(admin.ModelAdmin):
    form = MembershipAdminForm
    list_display = ("user", "school", "role", "is_active")
    list_filter = ("role", "is_active", "school")
    search_fields = ("user__username", "user__email", "school__name")
    autocomplete_fields = ("user", "school")
    inlines = (TeachingAssignmentInline,)

    def get_readonly_fields(self, request, obj=None):
        if obj is not None:
            return ("user", "school")
        return ()

    def save_model(self, request, obj, form, change):
        if not change:
            obj.full_clean()
            obj.save()
            return
        updated = update_membership(
            obj.pk,
            role=obj.role,
            is_active=obj.is_active,
        )
        obj.role = updated.role
        obj.is_active = updated.is_active

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(TeachingAssignment)
class TeachingAssignmentAdmin(admin.ModelAdmin):
    list_display = ("membership", "group", "subject")
    list_filter = ("membership__school",)
    search_fields = (
        "membership__user__username",
        "group__name",
        "subject__name",
    )
    autocomplete_fields = ("membership",)
