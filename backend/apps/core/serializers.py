from rest_framework import serializers


class HealthResponseSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=("ok", "unavailable"))
    database = serializers.ChoiceField(choices=("ok", "error"))


class ApiErrorDetailSerializer(serializers.Serializer):
    code = serializers.CharField()
    message = serializers.CharField()
    fields = serializers.JSONField(required=False)


class ApiErrorSerializer(serializers.Serializer):
    error = ApiErrorDetailSerializer()
