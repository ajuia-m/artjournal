from django.db.models import Prefetch
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView
from rest_framework_simplejwt.views import (
    TokenBlacklistView,
    TokenObtainPairView,
    TokenRefreshView,
)

from apps.accounts.models import User
from apps.accounts.serializers import CurrentUserSerializer
from apps.schools.models import Membership


class AuthThrottleMixin:
    throttle_classes = (ScopedRateThrottle,)
    throttle_scope = "auth"


class LoginView(AuthThrottleMixin, TokenObtainPairView):
    pass


class RefreshView(AuthThrottleMixin, TokenRefreshView):
    pass


class LogoutView(AuthThrottleMixin, TokenBlacklistView):
    pass


class CurrentUserView(APIView):
    permission_classes = (IsAuthenticated,)

    def get(self, request) -> Response:
        memberships = (
            Membership.objects.filter(is_active=True)
            .select_related("school")
            .prefetch_related(
                "teaching_assignments__group",
                "teaching_assignments__subject",
            )
        )
        user = User.objects.prefetch_related(
            Prefetch(
                "school_memberships",
                queryset=memberships,
                to_attr="active_school_memberships",
            )
        ).get(pk=request.user.pk)
        return Response(CurrentUserSerializer(user).data)
