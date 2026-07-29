import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Any

from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import IntegrityError, transaction
from django.db.models import Model
from django.utils import timezone

from apps.audit.models import LegacyAuditEntry
from apps.curriculum.models import (
    GroupSubject,
    ScheduleEntry,
    Subject,
    Topic,
    TopicCriterion,
    TopicGroupAssignment,
    TopicPeriodAssignment,
)
from apps.education.models import (
    AcademicPeriod,
    AcademicYear,
    CalendarException,
    Enrollment,
    Group,
    Student,
)
from apps.imports.exceptions import (
    ArtJournalImportError,
    ImportConflictError,
    ImportInProgressError,
    ImportValidationError,
)
from apps.imports.models import ImportBatch, LegacyObjectMap
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
from apps.imports.validation import ValidationResult, issue, validate_backup_document
from apps.journal.models import (
    CriterionScore,
    Lesson,
    LessonTopic,
    StudentLessonState,
    StudentTopicProgress,
)
from apps.schools.models import School
from apps.tuition.models import Payment


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"Non-standard JSON constant: {value}")


def _decode_document(raw_bytes: bytes, checksum: str) -> dict[str, Any]:
    try:
        decoded = raw_bytes.decode("utf-8")
    except UnicodeDecodeError as exception:
        report = _base_report(checksum=checksum)
        report["status"] = "failed"
        report["errors"] = [issue("invalid_encoding", "$", "Backup must be encoded as UTF-8.")]
        raise ImportValidationError("Backup is not valid UTF-8.", report) from exception

    try:
        document = json.loads(decoded, parse_constant=_reject_json_constant)
    except (json.JSONDecodeError, ValueError) as exception:
        report = _base_report(checksum=checksum)
        report["status"] = "failed"
        line_number = getattr(exception, "lineno", 1)
        report["errors"] = [issue("invalid_json", "$", f"Invalid JSON at line {line_number}.")]
        raise ImportValidationError("Backup is not valid JSON.", report) from exception

    if not isinstance(document, dict):
        report = _base_report(checksum=checksum)
        report["status"] = "failed"
        report["errors"] = [issue("schema_error", "$", "Backup root must be a JSON object.")]
        raise ImportValidationError("Backup root must be an object.", report)
    return document


def _base_report(
    *,
    checksum: str,
    document: dict[str, Any] | None = None,
    dry_run: bool = False,
) -> dict[str, Any]:
    document = document or {}
    return {
        "status": "pending",
        "dryRun": dry_run,
        "reused": False,
        "batchId": None,
        "exportId": document.get("exportId"),
        "checksum": checksum,
        "sourceCounts": {},
        "createdCounts": {},
        "errors": [],
        "warnings": [],
    }


def _report_from_validation(
    *,
    checksum: str,
    document: dict[str, Any],
    validation: ValidationResult,
    dry_run: bool,
) -> dict[str, Any]:
    report = _base_report(checksum=checksum, document=document, dry_run=dry_run)
    report["sourceCounts"] = validation.source_counts
    report["errors"] = validation.errors
    report["warnings"] = validation.warnings
    report["status"] = "validated" if validation.is_valid else "failed"
    return report


def _safe_exported_at(document: dict[str, Any]) -> Any:
    value = document.get("exportedAtEpochMillis")
    if not isinstance(value, int) or value < 0:
        return None
    try:
        return parse_epoch_millis(value)
    except (OverflowError, OSError, ValueError):
        return None


def _batch_defaults(document: dict[str, Any], checksum: str, school: School) -> dict[str, Any]:
    format_version = document.get("formatVersion")
    if not isinstance(format_version, int) or format_version < 1:
        format_version = 1
    backup_format = document.get("format")
    if not isinstance(backup_format, str):
        backup_format = ""
    source = document.get("source")
    if not isinstance(source, dict):
        source = {}
    return {
        "school": school,
        "checksum": checksum,
        "format": backup_format[:100],
        "format_version": format_version,
        "source": source,
        "exported_at": _safe_exported_at(document),
        "status": ImportBatch.Status.RUNNING,
    }


def _existing_batch_result(
    batch: ImportBatch,
    *,
    checksum: str,
    document: dict[str, Any],
    school: School,
) -> tuple[ImportBatch, dict[str, Any] | None]:
    if batch.school_id != school.id:
        report = _base_report(checksum=checksum, document=document)
        report.update(
            {
                "status": "conflict",
                "batchId": str(batch.id),
                "errors": [
                    issue(
                        "export_id_school_conflict",
                        "exportId",
                        "This exportId is already associated with another school.",
                    )
                ],
            }
        )
        raise ImportConflictError("exportId school conflict.", report)

    if batch.checksum != checksum:
        report = _base_report(checksum=checksum, document=document)
        report.update(
            {
                "status": "conflict",
                "batchId": str(batch.id),
                "errors": [
                    issue(
                        "export_id_checksum_conflict",
                        "exportId",
                        "This exportId is already associated with different file contents.",
                    )
                ],
            }
        )
        raise ImportConflictError("exportId checksum conflict.", report)

    if batch.status == ImportBatch.Status.SUCCEEDED:
        report = dict(batch.report)
        report["reused"] = True
        return batch, report

    if batch.status == ImportBatch.Status.RUNNING:
        report = _base_report(checksum=checksum, document=document)
        report.update(
            {
                "status": "in_progress",
                "batchId": str(batch.id),
                "errors": [
                    issue(
                        "import_in_progress",
                        "exportId",
                        "An import for this exportId is already running.",
                    )
                ],
            }
        )
        raise ImportInProgressError("Import is already running.", report)

    batch.status = ImportBatch.Status.RUNNING
    batch.finished_at = None
    batch.report = {}
    batch.counts = {}
    batch.save(update_fields=["status", "finished_at", "report", "counts", "updated_at"])
    return batch, None


def _claim_batch(
    *,
    document: dict[str, Any],
    checksum: str,
    school: School,
) -> tuple[ImportBatch, dict[str, Any] | None]:
    export_id = document.get("exportId")
    if not isinstance(export_id, str) or not export_id or len(export_id) > 255:
        report = _base_report(checksum=checksum, document=document)
        report["status"] = "failed"
        report["errors"] = [
            issue(
                "invalid_export_id",
                "exportId",
                "A non-empty exportId of at most 255 characters is required.",
            )
        ]
        raise ImportValidationError("Cannot create an import batch without exportId.", report)

    with transaction.atomic():
        existing = ImportBatch.objects.select_for_update().filter(export_id=export_id).first()
        if existing is not None:
            return _existing_batch_result(
                existing,
                checksum=checksum,
                document=document,
                school=school,
            )

    try:
        with transaction.atomic():
            batch = ImportBatch.objects.create(
                export_id=export_id,
                **_batch_defaults(document, checksum, school),
            )
    except IntegrityError:
        with transaction.atomic():
            existing = ImportBatch.objects.select_for_update().get(export_id=export_id)
            return _existing_batch_result(
                existing,
                checksum=checksum,
                document=document,
                school=school,
            )
    return batch, None


def _fail_batch(batch: ImportBatch, report: dict[str, Any]) -> None:
    report["batchId"] = str(batch.id)
    report["status"] = "failed"
    batch.status = ImportBatch.Status.FAILED
    batch.report = report
    batch.counts = {
        "source": report.get("sourceCounts", {}),
        "created": {},
    }
    batch.finished_at = timezone.now()
    batch.save(
        update_fields=[
            "status",
            "report",
            "counts",
            "finished_at",
            "updated_at",
        ]
    )


@dataclass
class ImportState:
    batch: ImportBatch
    school: School
    created: Counter[str] = field(default_factory=Counter)
    academic_years: dict[int, AcademicYear] = field(default_factory=dict)
    periods: dict[int, AcademicPeriod] = field(default_factory=dict)
    groups: dict[int, Group] = field(default_factory=dict)
    students: dict[int, Student] = field(default_factory=dict)
    topics: dict[int, Topic] = field(default_factory=dict)
    topic_criteria: dict[int, dict[str, TopicCriterion]] = field(
        default_factory=lambda: defaultdict(dict)
    )
    lessons: dict[int, Lesson] = field(default_factory=dict)

    def save(self, instance: Model) -> Model:
        instance.full_clean()
        instance.save()
        self.created[instance._meta.label_lower] += 1
        return instance

    def record_legacy(
        self,
        entity_type: str,
        legacy_local_id: int,
        instance: Model,
    ) -> None:
        LegacyObjectMap.objects.create(
            import_batch=self.batch,
            entity_type=entity_type,
            legacy_local_id=legacy_local_id,
            server_model=instance._meta.label_lower,
            server_object_id=instance.pk,
        )
        self.created[LegacyObjectMap._meta.label_lower] += 1

    def subject(self, name: str) -> Subject:
        subject, created = Subject.objects.get_or_create(
            school=self.school,
            name=name,
        )
        if created:
            self.created[Subject._meta.label_lower] += 1
        return subject

    def assign_subject(self, group: Group, subject: Subject) -> None:
        _assignment, created = GroupSubject.objects.get_or_create(
            group=group,
            subject=subject,
        )
        if created:
            self.created[GroupSubject._meta.label_lower] += 1


class ArtJournalImporter:
    def import_bytes(
        self,
        raw_bytes: bytes,
        *,
        school: School,
        dry_run: bool = False,
    ) -> dict[str, Any]:
        checksum = hashlib.sha256(raw_bytes).hexdigest()
        document = _decode_document(raw_bytes, checksum)

        if dry_run:
            validation = validate_backup_document(document)
            report = _report_from_validation(
                checksum=checksum,
                document=document,
                validation=validation,
                dry_run=True,
            )
            if not validation.is_valid:
                raise ImportValidationError("Backup validation failed.", report)
            return report

        batch, reused_report = _claim_batch(
            document=document,
            checksum=checksum,
            school=school,
        )
        if reused_report is not None:
            return reused_report

        validation = validate_backup_document(document)
        report = _report_from_validation(
            checksum=checksum,
            document=document,
            validation=validation,
            dry_run=False,
        )
        if not validation.is_valid:
            _fail_batch(batch, report)
            raise ImportValidationError("Backup validation failed.", report)

        state = ImportState(batch=batch, school=school)
        try:
            with transaction.atomic():
                self._import_document(document["data"], state)
        except (DjangoValidationError, IntegrityError) as exception:
            report["errors"].append(
                issue(
                    "database_constraint",
                    "$",
                    "Import violates a server model constraint; no domain records were committed.",
                )
            )
            _fail_batch(batch, report)
            raise ArtJournalImportError(
                "Import violates a model constraint.",
                report,
            ) from exception
        except Exception as exception:
            report["errors"].append(
                issue(
                    "import_aborted",
                    "$",
                    "Import aborted; no domain records were committed.",
                )
            )
            _fail_batch(batch, report)
            raise ArtJournalImportError("Import aborted.", report) from exception

        created_counts = dict(sorted(state.created.items()))
        report.update(
            {
                "status": "succeeded",
                "batchId": str(batch.id),
                "createdCounts": created_counts,
            }
        )
        batch.status = ImportBatch.Status.SUCCEEDED
        batch.counts = {
            "source": validation.source_counts,
            "created": created_counts,
        }
        batch.report = report
        batch.finished_at = timezone.now()
        batch.save(
            update_fields=[
                "status",
                "counts",
                "report",
                "finished_at",
                "updated_at",
            ]
        )
        return report

    def _import_document(self, data: dict[str, Any], state: ImportState) -> None:
        self._import_academic_years(data["academicYears"], state)
        self._import_periods(data["quarters"], state)
        self._import_groups(data["groups"], state)
        self._import_students(data["students"], state)
        self._import_payments(data["payments"], state)
        self._import_topics(data["topics"], state)
        self._import_lessons(data["lessons"], state)
        self._import_student_lesson_states(data["studentLessonStates"], state)
        self._import_student_topic_progress(data["studentTopicProgress"], state)
        self._import_audit_logs(data["auditLogs"], state)

    def _import_academic_years(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            academic_year = state.save(
                AcademicYear(
                    school=state.school,
                    name=record["name"],
                    is_active=record["isActive"],
                )
            )
            assert isinstance(academic_year, AcademicYear)
            state.academic_years[record["id"]] = academic_year
            state.record_legacy("academic_year", record["id"], academic_year)
            for holiday in parse_csv(record["holidays"]):
                state.save(
                    CalendarException(
                        academic_year=academic_year,
                        date=parse_date(holiday),
                        kind=CalendarException.Kind.HOLIDAY,
                    )
                )

    def _import_periods(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        position_by_year: defaultdict[int, int] = defaultdict(int)
        for record in records:
            year_id = record["academicYearId"]
            period = state.save(
                AcademicPeriod(
                    academic_year=state.academic_years[year_id],
                    name=record["name"],
                    kind=AcademicPeriod.Kind.QUARTER,
                    start_date=parse_date(record["startDate"]),
                    end_date=parse_date(record["endDate"]),
                    position=position_by_year[year_id],
                )
            )
            assert isinstance(period, AcademicPeriod)
            position_by_year[year_id] += 1
            state.periods[record["id"]] = period
            state.record_legacy("academic_period", record["id"], period)

    def _import_groups(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            group = state.save(
                Group(
                    academic_year=state.academic_years[record["academicYearId"]],
                    name=record["name"],
                )
            )
            assert isinstance(group, Group)
            state.groups[record["id"]] = group
            state.record_legacy("group", record["id"], group)

            subjects: dict[str, Subject] = {}
            for name in parse_csv(record["disciplines"]):
                subject = state.subject(name)
                subjects[name] = subject
                state.assign_subject(group, subject)
            for day, subject_name in parse_schedule(record["schedule"]):
                subject = subjects[subject_name]
                state.save(
                    ScheduleEntry(
                        group=group,
                        subject=subject,
                        day_of_week=day,
                    )
                )

    def _import_students(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            student = state.save(
                Student(
                    school=state.school,
                    last_name=record["lastName"],
                    first_name=record["firstName"],
                    birthday=parse_date(record["birthday"], allow_blank=True),
                    contract_number=record["contractNumber"],
                    status=record["status"],
                    archive_date=parse_date(record.get("archiveDate") or "", allow_blank=True),
                    archive_reason=record.get("archiveReason") or "",
                    custom_fields=parse_custom_fields(record["customFields"]),
                )
            )
            assert isinstance(student, Student)
            state.students[record["id"]] = student
            state.record_legacy("student", record["id"], student)

            enrollment_status = (
                Enrollment.Status.ACTIVE
                if record["status"] == Student.Status.ACTIVE
                else Enrollment.Status.ARCHIVED
            )
            state.save(
                Enrollment(
                    student=student,
                    group=state.groups[record["groupId"]],
                    started_on=parse_date(record["enrollmentDate"]),
                    ended_on=parse_date(record.get("archiveDate") or "", allow_blank=True),
                    status=enrollment_status,
                )
            )

            if record.get("paperPaymentDate"):
                state.save(
                    Payment(
                        student=student,
                        date=parse_date(record["paperPaymentDate"]),
                        amount=parse_decimal(record["paperPaymentAmount"]),
                        currency=state.school.default_currency,
                        kind=Payment.Kind.MATERIALS,
                        comment="Imported legacy material payment.",
                    )
                )

    def _import_payments(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            payment = state.save(
                Payment(
                    student=state.students[record["studentId"]],
                    date=parse_date(record["date"]),
                    amount=parse_decimal(record["amount"]),
                    currency=state.school.default_currency,
                    kind=Payment.Kind.TUITION,
                    comment=record["comment"],
                )
            )
            assert isinstance(payment, Payment)
            state.record_legacy("payment", record["id"], payment)

    def _import_topics(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            subject = state.subject(record["discipline"])
            topic = state.save(
                Topic(
                    subject=subject,
                    name=record["name"],
                )
            )
            assert isinstance(topic, Topic)
            state.topics[record["id"]] = topic
            state.record_legacy("topic", record["id"], topic)

            for position, (name, maximum) in enumerate(parse_named_integers(record["criteria"])):
                criterion = state.save(
                    TopicCriterion(
                        topic=topic,
                        name=name,
                        max_points=maximum,
                        position=position,
                    )
                )
                assert isinstance(criterion, TopicCriterion)
                state.topic_criteria[record["id"]][name] = criterion

            for group_id in parse_id_csv(record["groupIds"]):
                group = state.groups[group_id]
                state.assign_subject(group, subject)
                state.save(
                    TopicGroupAssignment(
                        topic=topic,
                        group=group,
                    )
                )
            for period_id in parse_id_csv(record["quarterIds"]):
                state.save(
                    TopicPeriodAssignment(
                        topic=topic,
                        academic_period=state.periods[period_id],
                    )
                )

    def _import_lessons(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            group = state.groups[record["groupId"]]
            lesson_date = parse_date(record["date"])
            if record["isNonSchoolDay"]:
                calendar_exception, created = CalendarException.objects.get_or_create(
                    academic_year=group.academic_year,
                    group=group,
                    date=lesson_date,
                    kind=CalendarException.Kind.CANCELLATION,
                    defaults={"reason": record.get("customTopicName") or ""},
                )
                if created:
                    state.created[CalendarException._meta.label_lower] += 1
                state.record_legacy("lesson", record["id"], calendar_exception)
                continue

            subject = state.subject(record["discipline"])
            state.assign_subject(group, subject)
            lesson = state.save(
                Lesson(
                    group=group,
                    subject=subject,
                    date=lesson_date,
                    custom_topic_name=record.get("customTopicName") or "",
                )
            )
            assert isinstance(lesson, Lesson)
            state.lessons[record["id"]] = lesson
            state.record_legacy("lesson", record["id"], lesson)
            if record.get("topicId") is not None:
                state.save(
                    LessonTopic(
                        lesson=lesson,
                        topic=state.topics[record["topicId"]],
                        position=0,
                    )
                )

    def _import_student_lesson_states(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            lesson_state = state.save(
                StudentLessonState(
                    student=state.students[record["studentId"]],
                    lesson=state.lessons[record["lessonId"]],
                    grade=record.get("grade"),
                    is_present=record["isPresent"],
                    is_excused_absence=record["isExcusedAbsence"],
                    homework_points=record.get("homeworkPoints"),
                    comment=record.get("comment") or "",
                    note=record.get("note") or "",
                )
            )
            state.record_legacy(
                "student_lesson_state",
                record["id"],
                lesson_state,
            )

    def _import_student_topic_progress(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            progress = state.save(
                StudentTopicProgress(
                    student=state.students[record["studentId"]],
                    topic=state.topics[record["topicId"]],
                    stage=record["stage"],
                )
            )
            assert isinstance(progress, StudentTopicProgress)
            state.record_legacy(
                "student_topic_progress",
                record["id"],
                progress,
            )
            for criterion_name, score in parse_named_integers(record["criteriaGrades"]):
                state.save(
                    CriterionScore(
                        progress=progress,
                        criterion=state.topic_criteria[record["topicId"]][criterion_name],
                        score=score,
                    )
                )

    def _import_audit_logs(
        self,
        records: list[dict[str, Any]],
        state: ImportState,
    ) -> None:
        for record in records:
            entry = state.save(
                LegacyAuditEntry(
                    school=state.school,
                    import_batch=state.batch,
                    legacy_local_id=record["id"],
                    occurred_at=parse_epoch_millis(record["timestamp"]),
                    action=record["action"],
                    details=record["details"],
                    revert_data=record.get("revertData") or "",
                )
            )
            state.record_legacy("audit_log", record["id"], entry)
