from django.contrib import admin

from apps.schools.models import School


@admin.register(School)
class SchoolAdmin(admin.ModelAdmin):
    list_display = ("name", "slug", "default_currency")
    search_fields = ("name", "slug")
