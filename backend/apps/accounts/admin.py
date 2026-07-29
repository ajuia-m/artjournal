from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.contrib.auth.forms import UserChangeForm
from django.core.exceptions import ValidationError

from apps.accounts.models import User
from apps.schools.models import Membership


class ArtJournalUserChangeForm(UserChangeForm):
    class Meta(UserChangeForm.Meta):
        model = User

    def clean_is_active(self):
        is_active = self.cleaned_data["is_active"]
        user = self.instance
        if not user.pk or not user.is_active or is_active:
            return is_active

        administered_schools = Membership.objects.filter(
            user=user,
            role=Membership.Role.ADMIN,
            is_active=True,
        ).values_list("school_id", flat=True)
        for school_id in administered_schools:
            another_admin_exists = (
                Membership.objects.filter(
                    school_id=school_id,
                    role=Membership.Role.ADMIN,
                    is_active=True,
                    user__is_active=True,
                )
                .exclude(user=user)
                .exists()
            )
            if not another_admin_exists:
                raise ValidationError(
                    "This user is the last active administrator of one or more schools."
                )
        return is_active


@admin.register(User)
class ArtJournalUserAdmin(UserAdmin):
    form = ArtJournalUserChangeForm
