from django.urls import path

from apps.sync.views import ChangeFeedView, SyncCommandBatchView

urlpatterns = [
    path("commands/", SyncCommandBatchView.as_view(), name="sync-command-batch"),
    path("changes/", ChangeFeedView.as_view(), name="sync-change-feed"),
]
