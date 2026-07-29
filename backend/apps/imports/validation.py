import json
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from apps.imports.parsing import (
    parse_csv,
    parse_custom_fields,
    parse_date,
    parse_decimal,
    parse_epoch_millis,
    parse_id_csv,
    parse_named_integers,
    parse_schedule,
)

BACKUP_FORMAT = "artjournal-backup"
BACKUP_FORMAT_VERSION = 1
SCHEMA_PATH = Path(__file__).parent / "schemas" / "artjournal-backup-v1.schema.json"

SOURCE_ARRAYS = (
    "academicYears",
    "groups",
    "students",
    "payments",
    "quarters",
    "lessons",
    "studentLessonStates",
    "topics",
    "studentTopicProgress",
    "auditLogs",
)


@dataclass
class ValidationResult:
    errors: list[dict[str, str]] = field(default_factory=list)
    warnings: list[dict[str, str]] = field(default_factory=list)
    source_counts: dict[str, int] = field(default_factory=dict)

    @property
    def is_valid(self) -> bool:
        return not self.errors


def issue(code: str, path: str, message: str) -> dict[str, str]:
    return {"code": code, "path": path, "message": message}


@lru_cache(maxsize=1)
def load_schema() -> dict[str, Any]:
    with SCHEMA_PATH.open(encoding="utf-8") as schema_file:
        schema = json.load(schema_file)
    Draft202012Validator.check_schema(schema)
    return schema


def _schema_path(parts: Any) -> str:
    return ".".join(str(part) for part in parts) or "$"


def _duplicates(values: list[Any]) -> set[Any]:
    return {value for value, count in Counter(values).items() if count > 1}


def _parse_or_error(
    parser: Callable[[Any], Any],
    value: Any,
    *,
    path: str,
    result: ValidationResult,
    code: str,
    message: str,
) -> Any | None:
    try:
        return parser(value)
    except (TypeError, ValueError, OverflowError):
        result.errors.append(issue(code, path, message))
        return None


def _check_reference(
    value: int,
    targets: set[int],
    *,
    path: str,
    target_name: str,
    result: ValidationResult,
) -> None:
    if value not in targets:
        result.errors.append(
            issue(
                "missing_reference",
                path,
                f"Referenced {target_name} does not exist in this backup.",
            )
        )


def _validate_schema(document: object, result: ValidationResult) -> None:
    if isinstance(document, dict):
        if document.get("format") != BACKUP_FORMAT:
            result.errors.append(
                issue(
                    "unsupported_format",
                    "format",
                    f"Expected format {BACKUP_FORMAT}.",
                )
            )
        if document.get("formatVersion") != BACKUP_FORMAT_VERSION:
            result.errors.append(
                issue(
                    "unsupported_format_version",
                    "formatVersion",
                    f"Only formatVersion {BACKUP_FORMAT_VERSION} is supported.",
                )
            )

    validator = Draft202012Validator(load_schema())
    for error in sorted(validator.iter_errors(document), key=lambda item: list(item.absolute_path)):
        path = _schema_path(error.absolute_path)
        if error.validator == "const" and path in {"format", "formatVersion"}:
            continue
        result.errors.append(
            issue(
                "schema_error",
                path,
                error.message,
            )
        )


def _collect_source_ids(
    data: dict[str, list[dict[str, Any]]],
    result: ValidationResult,
) -> dict[str, set[int]]:
    source_ids: dict[str, set[int]] = {}
    for array_name in SOURCE_ARRAYS:
        records = data[array_name]
        result.source_counts[array_name] = len(records)
        ids = [record["id"] for record in records]
        for duplicate_id in sorted(_duplicates(ids)):
            result.errors.append(
                issue(
                    "duplicate_legacy_id",
                    f"data.{array_name}",
                    f"Legacy id {duplicate_id} occurs more than once in this array.",
                )
            )
        source_ids[array_name] = set(ids)
    return source_ids


def _validate_academic_years(
    records: list[dict[str, Any]],
    result: ValidationResult,
) -> None:
    active_count = sum(record["isActive"] for record in records)
    if active_count > 1:
        result.errors.append(
            issue(
                "multiple_active_academic_years",
                "data.academicYears",
                "A school can have at most one active academic year.",
            )
        )

    for index, record in enumerate(records):
        for field_name in ("holidays", "quarterMarkers"):
            path = f"data.academicYears.{index}.{field_name}"
            parsed_dates = parse_csv(record[field_name])
            for value in parsed_dates:
                _parse_or_error(
                    parse_date,
                    value,
                    path=path,
                    result=result,
                    code="invalid_date",
                    message="Expected a real calendar date in YYYY-MM-DD form.",
                )
            if _duplicates(parsed_dates):
                result.errors.append(
                    issue(
                        "duplicate_date",
                        path,
                        "The date list contains duplicates.",
                    )
                )
        if record["quarterMarkers"].strip():
            result.warnings.append(
                issue(
                    "quarter_markers_not_persisted",
                    f"data.academicYears.{index}.quarterMarkers",
                    (
                        "Quarter markers are validated but explicit academic periods "
                        "are authoritative."
                    ),
                )
            )


def _validate_groups(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    result: ValidationResult,
) -> dict[int, set[str]]:
    subjects_by_group: dict[int, set[str]] = {}
    for index, record in enumerate(records):
        _check_reference(
            record["academicYearId"],
            source_ids["academicYears"],
            path=f"data.groups.{index}.academicYearId",
            target_name="academic year",
            result=result,
        )
        subjects = parse_csv(record["disciplines"])
        subjects_by_group[record["id"]] = set(subjects)
        if _duplicates(subjects):
            result.errors.append(
                issue(
                    "duplicate_subject",
                    f"data.groups.{index}.disciplines",
                    "The discipline list contains duplicates.",
                )
            )
        if not subjects:
            result.warnings.append(
                issue(
                    "group_without_subjects",
                    f"data.groups.{index}.disciplines",
                    "The group has no disciplines.",
                )
            )

        schedule = _parse_or_error(
            parse_schedule,
            record["schedule"],
            path=f"data.groups.{index}.schedule",
            result=result,
            code="invalid_schedule",
            message="Expected comma-separated schedule entries in DAY:SUBJECT form.",
        )
        if schedule is None:
            continue
        days = [day for day, _subject in schedule]
        if _duplicates(days):
            result.errors.append(
                issue(
                    "duplicate_schedule_day",
                    f"data.groups.{index}.schedule",
                    "Only one scheduled discipline is allowed for each weekday in JSON v1.",
                )
            )
        for day, subject in schedule:
            if day < 1 or day > 7:
                result.errors.append(
                    issue(
                        "invalid_schedule_day",
                        f"data.groups.{index}.schedule",
                        "Schedule weekday must be between 1 and 7.",
                    )
                )
            if subject not in subjects_by_group[record["id"]]:
                result.errors.append(
                    issue(
                        "unknown_schedule_subject",
                        f"data.groups.{index}.schedule",
                        "Scheduled discipline is absent from the group discipline list.",
                    )
                )
    return subjects_by_group


def _validate_students(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    result: ValidationResult,
) -> None:
    maximum_payment = parse_decimal(9_999_999_999.99)
    for index, record in enumerate(records):
        _check_reference(
            record["groupId"],
            source_ids["groups"],
            path=f"data.students.{index}.groupId",
            target_name="group",
            result=result,
        )
        parsed_dates = {}
        for field_name in ("birthday", "enrollmentDate", "archiveDate"):
            value = record.get(field_name)
            if value:
                parsed_dates[field_name] = _parse_or_error(
                    parse_date,
                    value,
                    path=f"data.students.{index}.{field_name}",
                    result=result,
                    code="invalid_date",
                    message="Expected a real calendar date in YYYY-MM-DD form.",
                )
        if not record["enrollmentDate"]:
            result.errors.append(
                issue(
                    "missing_enrollment_date",
                    f"data.students.{index}.enrollmentDate",
                    "Enrollment date is required by the normalized server model.",
                )
            )
        elif record.get("archiveDate"):
            enrollment_date = parsed_dates.get("enrollmentDate")
            archive_date = parsed_dates.get("archiveDate")
            if enrollment_date and archive_date and archive_date < enrollment_date:
                result.errors.append(
                    issue(
                        "inverted_enrollment_range",
                        f"data.students.{index}",
                        "Archive date must not be before enrollment date.",
                    )
                )

        payment_date = record.get("paperPaymentDate")
        payment_amount = record.get("paperPaymentAmount")
        if (payment_date is None or payment_date == "") != (payment_amount is None):
            result.errors.append(
                issue(
                    "incomplete_material_payment",
                    f"data.students.{index}",
                    (
                        "Material payment date and amount must either both be present "
                        "or both be absent."
                    ),
                )
            )
        if payment_date:
            _parse_or_error(
                parse_date,
                payment_date,
                path=f"data.students.{index}.paperPaymentDate",
                result=result,
                code="invalid_date",
                message="Expected a real calendar date in YYYY-MM-DD form.",
            )
        if payment_amount is not None and parse_decimal(payment_amount) > maximum_payment:
            result.errors.append(
                issue(
                    "amount_too_large",
                    f"data.students.{index}.paperPaymentAmount",
                    "Amount exceeds the server decimal field capacity.",
                )
            )
        _parse_or_error(
            parse_custom_fields,
            record["customFields"],
            path=f"data.students.{index}.customFields",
            result=result,
            code="invalid_custom_fields",
            message="Expected custom fields in KEY::VALUE entries separated by ||.",
        )


def _validate_payments(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    result: ValidationResult,
) -> None:
    maximum = parse_decimal(9_999_999_999.99)
    for index, record in enumerate(records):
        _check_reference(
            record["studentId"],
            source_ids["students"],
            path=f"data.payments.{index}.studentId",
            target_name="student",
            result=result,
        )
        _parse_or_error(
            parse_date,
            record["date"],
            path=f"data.payments.{index}.date",
            result=result,
            code="invalid_date",
            message="Expected a real calendar date in YYYY-MM-DD form.",
        )
        amount = parse_decimal(record["amount"])
        if amount > maximum:
            result.errors.append(
                issue(
                    "amount_too_large",
                    f"data.payments.{index}.amount",
                    "Amount exceeds the server decimal field capacity.",
                )
            )


def _validate_periods(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    result: ValidationResult,
) -> None:
    for index, record in enumerate(records):
        _check_reference(
            record["academicYearId"],
            source_ids["academicYears"],
            path=f"data.quarters.{index}.academicYearId",
            target_name="academic year",
            result=result,
        )
        start = _parse_or_error(
            parse_date,
            record["startDate"],
            path=f"data.quarters.{index}.startDate",
            result=result,
            code="invalid_date",
            message="Expected a real calendar date in YYYY-MM-DD form.",
        )
        end = _parse_or_error(
            parse_date,
            record["endDate"],
            path=f"data.quarters.{index}.endDate",
            result=result,
            code="invalid_date",
            message="Expected a real calendar date in YYYY-MM-DD form.",
        )
        if start and end and start > end:
            result.errors.append(
                issue(
                    "inverted_date_range",
                    f"data.quarters.{index}",
                    "Academic period start date must not be after its end date.",
                )
            )


def _validate_topics(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    subjects_by_group: dict[int, set[str]],
    result: ValidationResult,
) -> dict[int, dict[str, int]]:
    criteria_by_topic: dict[int, dict[str, int]] = {}
    for index, record in enumerate(records):
        criteria = _parse_or_error(
            parse_named_integers,
            record["criteria"],
            path=f"data.topics.{index}.criteria",
            result=result,
            code="invalid_criteria",
            message="Expected criteria in NAME:POSITIVE_INTEGER form.",
        )
        if criteria is not None:
            names = [name for name, _maximum in criteria]
            if _duplicates(names):
                result.errors.append(
                    issue(
                        "duplicate_criterion",
                        f"data.topics.{index}.criteria",
                        "Criterion names must be unique within a topic.",
                    )
                )
            for _name, maximum in criteria:
                if maximum <= 0:
                    result.errors.append(
                        issue(
                            "invalid_criterion_maximum",
                            f"data.topics.{index}.criteria",
                            "Criterion maximum must be positive.",
                        )
                    )
            criteria_by_topic[record["id"]] = dict(criteria)

        for field_name, target_array, target_name in (
            ("groupIds", "groups", "group"),
            ("quarterIds", "quarters", "academic period"),
        ):
            parsed_ids = _parse_or_error(
                parse_id_csv,
                record[field_name],
                path=f"data.topics.{index}.{field_name}",
                result=result,
                code="invalid_id_list",
                message="Expected a comma-separated list of positive integer IDs.",
            )
            if parsed_ids is None:
                continue
            if _duplicates(parsed_ids):
                result.errors.append(
                    issue(
                        "duplicate_reference",
                        f"data.topics.{index}.{field_name}",
                        "The reference list contains duplicates.",
                    )
                )
            for target_id in parsed_ids:
                _check_reference(
                    target_id,
                    source_ids[target_array],
                    path=f"data.topics.{index}.{field_name}",
                    target_name=target_name,
                    result=result,
                )
                if (
                    field_name == "groupIds"
                    and target_id in subjects_by_group
                    and record["discipline"] not in subjects_by_group[target_id]
                ):
                    result.warnings.append(
                        issue(
                            "topic_subject_added_to_group",
                            f"data.topics.{index}.groupIds",
                            "Topic discipline will be added to the assigned group.",
                        )
                    )
    return criteria_by_topic


def _validate_lessons(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    subjects_by_group: dict[int, set[str]],
    topics_by_id: dict[int, dict[str, Any]],
    result: ValidationResult,
) -> set[int]:
    non_school_lesson_ids: set[int] = set()
    for index, record in enumerate(records):
        _check_reference(
            record["groupId"],
            source_ids["groups"],
            path=f"data.lessons.{index}.groupId",
            target_name="group",
            result=result,
        )
        _parse_or_error(
            parse_date,
            record["date"],
            path=f"data.lessons.{index}.date",
            result=result,
            code="invalid_date",
            message="Expected a real calendar date in YYYY-MM-DD form.",
        )
        topic_id = record.get("topicId")
        if topic_id is not None:
            _check_reference(
                topic_id,
                source_ids["topics"],
                path=f"data.lessons.{index}.topicId",
                target_name="topic",
                result=result,
            )
        if record["isNonSchoolDay"]:
            non_school_lesson_ids.add(record["id"])
            if topic_id is not None:
                result.warnings.append(
                    issue(
                        "non_school_topic_ignored",
                        f"data.lessons.{index}.topicId",
                        "A non-school-day record becomes CalendarException; its topic is ignored.",
                    )
                )
            continue

        if (
            topic_id in topics_by_id
            and topics_by_id[topic_id]["discipline"] != record["discipline"]
        ):
            result.errors.append(
                issue(
                    "lesson_topic_subject_mismatch",
                    f"data.lessons.{index}.topicId",
                    "Lesson and topic disciplines must match.",
                )
            )
        if (
            record["groupId"] in subjects_by_group
            and record["discipline"] not in subjects_by_group[record["groupId"]]
        ):
            result.warnings.append(
                issue(
                    "lesson_subject_added_to_group",
                    f"data.lessons.{index}.discipline",
                    "Lesson discipline will be added to the group.",
                )
            )
    return non_school_lesson_ids


def _validate_student_lesson_states(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    non_school_lesson_ids: set[int],
    result: ValidationResult,
) -> None:
    pairs: list[tuple[int, int]] = []
    for index, record in enumerate(records):
        _check_reference(
            record["studentId"],
            source_ids["students"],
            path=f"data.studentLessonStates.{index}.studentId",
            target_name="student",
            result=result,
        )
        _check_reference(
            record["lessonId"],
            source_ids["lessons"],
            path=f"data.studentLessonStates.{index}.lessonId",
            target_name="lesson",
            result=result,
        )
        if record["lessonId"] in non_school_lesson_ids:
            result.errors.append(
                issue(
                    "state_for_non_school_day",
                    f"data.studentLessonStates.{index}.lessonId",
                    "Student state cannot reference a non-school-day record.",
                )
            )
        if record["isPresent"] and record["isExcusedAbsence"]:
            result.errors.append(
                issue(
                    "inconsistent_attendance",
                    f"data.studentLessonStates.{index}",
                    "A present student cannot simultaneously have an excused absence.",
                )
            )
        pairs.append((record["studentId"], record["lessonId"]))
    if _duplicates(pairs):
        result.errors.append(
            issue(
                "duplicate_student_lesson_state",
                "data.studentLessonStates",
                "Only one state is allowed for each student and lesson pair.",
            )
        )


def _validate_progress(
    records: list[dict[str, Any]],
    source_ids: dict[str, set[int]],
    criteria_by_topic: dict[int, dict[str, int]],
    result: ValidationResult,
) -> None:
    pairs: list[tuple[int, int]] = []
    for index, record in enumerate(records):
        _check_reference(
            record["studentId"],
            source_ids["students"],
            path=f"data.studentTopicProgress.{index}.studentId",
            target_name="student",
            result=result,
        )
        _check_reference(
            record["topicId"],
            source_ids["topics"],
            path=f"data.studentTopicProgress.{index}.topicId",
            target_name="topic",
            result=result,
        )
        grades = _parse_or_error(
            parse_named_integers,
            record["criteriaGrades"],
            path=f"data.studentTopicProgress.{index}.criteriaGrades",
            result=result,
            code="invalid_criterion_scores",
            message="Expected criterion scores in NAME:NON_NEGATIVE_INTEGER form.",
        )
        if grades is not None:
            names = [name for name, _score in grades]
            if _duplicates(names):
                result.errors.append(
                    issue(
                        "duplicate_criterion_score",
                        f"data.studentTopicProgress.{index}.criteriaGrades",
                        "A criterion can have only one score.",
                    )
                )
            maxima = criteria_by_topic.get(record["topicId"], {})
            for name, score in grades:
                if name not in maxima:
                    result.errors.append(
                        issue(
                            "unknown_criterion",
                            f"data.studentTopicProgress.{index}.criteriaGrades",
                            "A score references a criterion absent from the topic.",
                        )
                    )
                elif score < 0 or score > maxima[name]:
                    result.errors.append(
                        issue(
                            "criterion_score_out_of_range",
                            f"data.studentTopicProgress.{index}.criteriaGrades",
                            "Criterion score must be between zero and its configured maximum.",
                        )
                    )
        pairs.append((record["studentId"], record["topicId"]))
    if _duplicates(pairs):
        result.errors.append(
            issue(
                "duplicate_student_topic_progress",
                "data.studentTopicProgress",
                "Only one progress record is allowed for each student and topic pair.",
            )
        )


def _validate_audit_logs(
    records: list[dict[str, Any]],
    result: ValidationResult,
) -> None:
    for index, record in enumerate(records):
        _parse_or_error(
            parse_epoch_millis,
            record["timestamp"],
            path=f"data.auditLogs.{index}.timestamp",
            result=result,
            code="invalid_timestamp",
            message="Timestamp is outside the supported date range.",
        )


def validate_backup_document(document: object) -> ValidationResult:
    result = ValidationResult()
    _validate_schema(document, result)
    if result.errors:
        return result

    assert isinstance(document, dict)
    export_id = document["exportId"]
    if len(export_id) > 255:
        result.errors.append(
            issue(
                "export_id_too_long",
                "exportId",
                "exportId must not exceed 255 characters.",
            )
        )
    _parse_or_error(
        parse_epoch_millis,
        document["exportedAtEpochMillis"],
        path="exportedAtEpochMillis",
        result=result,
        code="invalid_timestamp",
        message="Timestamp is outside the supported date range.",
    )

    data = document["data"]
    source_ids = _collect_source_ids(data, result)
    _validate_academic_years(data["academicYears"], result)
    subjects_by_group = _validate_groups(data["groups"], source_ids, result)
    _validate_students(data["students"], source_ids, result)
    _validate_payments(data["payments"], source_ids, result)
    _validate_periods(data["quarters"], source_ids, result)
    criteria_by_topic = _validate_topics(
        data["topics"],
        source_ids,
        subjects_by_group,
        result,
    )
    topics_by_id = {record["id"]: record for record in data["topics"]}
    non_school_lesson_ids = _validate_lessons(
        data["lessons"],
        source_ids,
        subjects_by_group,
        topics_by_id,
        result,
    )
    _validate_student_lesson_states(
        data["studentLessonStates"],
        source_ids,
        non_school_lesson_ids,
        result,
    )
    _validate_progress(
        data["studentTopicProgress"],
        source_ids,
        criteria_by_topic,
        result,
    )
    _validate_audit_logs(data["auditLogs"], result)
    return result
