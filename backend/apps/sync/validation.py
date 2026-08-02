import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker
from rest_framework import serializers

SYNC_SCHEMA_PATH = Path(__file__).parent / "schemas" / "sync-protocol-v1.schema.json"


@lru_cache(maxsize=1)
def sync_protocol_validator() -> Draft202012Validator:
    schema = json.loads(SYNC_SCHEMA_PATH.read_text(encoding="utf-8"))
    return Draft202012Validator(schema, format_checker=FormatChecker())


def validate_sync_protocol(data: Any) -> None:
    errors = sorted(sync_protocol_validator().iter_errors(data), key=lambda item: list(item.path))
    if not errors:
        return

    messages = []
    for error in errors:
        path = ".".join(str(part) for part in error.absolute_path) or "$"
        messages.append(f"{path}: {error.message}")
    raise serializers.ValidationError({"schema": messages})
