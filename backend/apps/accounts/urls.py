from django.urls import path

from apps.accounts.views import CurrentUserView, LoginView, LogoutView, RefreshView

urlpatterns = [
    path("token/", LoginView.as_view(), name="token-obtain-pair"),
    path("token/refresh/", RefreshView.as_view(), name="token-refresh"),
    path("token/logout/", LogoutView.as_view(), name="token-logout"),
    path("me/", CurrentUserView.as_view(), name="current-user"),
]
