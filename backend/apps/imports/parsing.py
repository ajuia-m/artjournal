from datetime import UTC, date, datetime
from decimal import Decimal, InvalidOperation


def parse_date(value: str, *, allow_blank: bool = False) -> date | None:
    if allow_blank and not value:
        return None
    return date.fromisoformat(value)


def parse_epoch_millis(value: int) -> datetime:
    return datetime.fromtimestamp(value / 1000, tz=UTC)


def parse_decimal(value: int | float) -> Decimal:
    try:
        return Decimal(str(value))
    except InvalidOperation as exception:
        raise ValueError("Value is not a valid decimal number.") from exception


def parse_csv(value: str) -> list[str]:
    if not value.strip():
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def parse_id_csv(value: str) -> list[int]:
    return [int(item) for item in parse_csv(value)]


def parse_named_integers(value: str) -> list[tuple[str, int]]:
    result: list[tuple[str, int]] = []
    for item in parse_csv(value):
        name, separator, raw_number = item.rpartition(":")
        if not separator or not name.strip() or not raw_number.strip():
            raise ValueError("Expected comma-separated entries in NAME:INTEGER form.")
        result.append((name.strip(), int(raw_number.strip())))
    return result


def parse_schedule(value: str) -> list[tuple[int, str]]:
    result: list[tuple[int, str]] = []
    for item in parse_csv(value):
        raw_day, separator, subject = item.partition(":")
        if not separator or not raw_day.strip() or not subject.strip():
            raise ValueError("Expected comma-separated entries in DAY:SUBJECT form.")
        result.append((int(raw_day.strip()), subject.strip()))
    return result


def parse_custom_fields(value: str) -> dict[str, str]:
    if not value.strip():
        return {}
    result: dict[str, str] = {}
    for item in value.split("||"):
        key, separator, field_value = item.partition("::")
        if not separator or not key.strip():
            raise ValueError("Expected custom fields in KEY::VALUE form.")
        normalized_key = key.strip()
        if normalized_key in result:
            raise ValueError(f"Duplicate custom field: {normalized_key}.")
        result[normalized_key] = field_value
    return result
