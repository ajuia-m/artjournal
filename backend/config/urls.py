from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/v1/schema/", SpectacularAPIView.as_view(), name="api-schema"),
    path(
        "api/v1/docs/",
        SpectacularSwaggerView.as_view(url_name="api-schema"),
        name="api-docs",
    ),
    path("api/v1/auth/", include("apps.accounts.urls")),
    path(
        "api/v1/schools/<uuid:school_id>/groups/<uuid:group_id>/",
        include("apps.journal.urls"),
    ),
    path("api/v1/schools/", include("apps.schools.urls")),
    path("api/v1/", include("apps.core.urls")),
]
