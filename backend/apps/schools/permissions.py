from typing import Any

from rest_framework.permissions import BasePermission

from apps.schools.access import (
    GroupAction,
    SchoolAction,
    has_group_action,
    has_school_action,
)


class HasSchoolAction(BasePermission):
    message = "You do not have permission for this school action."

    def _authorize(
        self,
        request: Any,
        view: Any,
        obj: Any | None = None,
    ) -> bool:
        action = getattr(view, "required_school_action", None)
        resolver = getattr(view, "get_permission_school", None)
        if action is None or resolver is None:
            return False
        try:
            normalized_action = SchoolAction(action)
            school = resolver(request, obj)
        except (TypeError, ValueError):
            return False
        if school is None:
            return False
        return has_school_action(request.user, school, normalized_action)

    def has_permission(self, request: Any, view: Any) -> bool:
        return self._authorize(request, view)

    def has_object_permission(
        self,
        request: Any,
        view: Any,
        obj: Any,
    ) -> bool:
        return self._authorize(request, view, obj)


class HasGroupAction(BasePermission):
    message = "You do not have permission for this group action."

    def _authorize(
        self,
        request: Any,
        view: Any,
        obj: Any | None = None,
    ) -> bool:
        action = getattr(view, "required_group_action", None)
        group_resolver = getattr(view, "get_permission_group", None)
        if action is None or group_resolver is None:
            return False
        subject_resolver = getattr(view, "get_permission_subject", None)
        try:
            normalized_action = GroupAction(action)
            group = group_resolver(request, obj)
            subject = subject_resolver(request, obj) if subject_resolver else None
        except (TypeError, ValueError):
            return False
        if group is None:
            return False
        return has_group_action(
            request.user,
            group,
            normalized_action,
            subject=subject,
        )

    def has_permission(self, request: Any, view: Any) -> bool:
        return self._authorize(request, view)

    def has_object_permission(
        self,
        request: Any,
        view: Any,
        obj: Any,
    ) -> bool:
        return self._authorize(request, view, obj)
