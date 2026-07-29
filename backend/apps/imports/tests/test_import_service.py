import json
from copy import deepcopy
from pathlib import Path

import pytest
from django.conf import settings

from apps.audit.models import AuditEvent, LegacyAuditEntry
from apps.curriculum.models import (
    GroupSubject,
    ScheduleEntry,
    Subject,
    Topic,
    TopicCriterion,
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
    ImportValidationError,
)
from apps.imports.models import ImportBatch, LegacyObjectMap
from apps.imports.service import ArtJournalImporter
from apps.journal.models import (
    CriterionScore,
    Lesson,
    LessonTopic,
    StudentLessonState,
    StudentTopicProgress,
)
from apps.schools.models import School
from apps.tuition.models import Payment

EXAMPLE_PATH = (
    Path(settings.BASE_DIR).parent / "docs" / "examples" / "artjournal-backup-v1.example.json"
)


@pytest.fixture
def school() -> School:
    return School.objects.create(
        name="Import target",
        slug="import-target",
        default_currency="RUB",
    )


@pytest.fixture
def backup_bytes() -> bytes:
    return EXAMPLE_PATH.read_bytes()


@pytest.fixture
def backup_document(backup_bytes: bytes) -> dict[str, object]:
    return json.loads(backup_bytes)


def encode(document: dict[str, object]) -> bytes:
    return json.dumps(document, ensure_ascii=False, sort_keys=True).encode()


def domain_counts() -> dict[str, int]:
    models = (
        AcademicYear,
        AcademicPeriod,
        Group,
        Student,
        Enrollment,
        CalendarException,
        Subject,
        GroupSubject,
        ScheduleEntry,
        Topic,
        TopicCriterion,
        Lesson,
        LessonTopic,
        StudentLessonState,
        StudentTopicProgress,
        CriterionScore,
        Payment,
        LegacyAuditEntry,
    )
    return {model._meta.label_lower: model.objects.count() for model in models}


@pytest.mark.django_db
def test_full_import_creates_normalized_domain_graph(
    school: School,
    backup_bytes: bytes,
) -> None:
    report = ArtJournalImporter().import_bytes(backup_bytes, school=school)

    assert report["status"] == "succeeded"
    assert report["reused"] is False
    assert ImportBatch.objects.get().status == ImportBatch.Status.SUCCEEDED
    assert AcademicYear.objects.count() == 1
    assert AcademicPeriod.objects.count() == 1
    assert Group.objects.count() == 1
    assert Student.objects.count() == 1
    assert Enrollment.objects.count() == 1
    assert Subject.objects.count() == 2
    assert GroupSubject.objects.count() == 2
    assert ScheduleEntry.objects.count() == 2
    assert Topic.objects.count() == 1
    assert TopicCriterion.objects.count() == 2
    assert Lesson.objects.count() == 2
    assert LessonTopic.objects.count() == 2
    assert CalendarException.objects.count() == 2
    assert StudentLessonState.objects.count() == 2
    assert StudentTopicProgress.objects.count() == 1
    assert CriterionScore.objects.count() == 2
    assert Payment.objects.count() == 2
    assert LegacyAuditEntry.objects.count() == 1
    assert AuditEvent.objects.count() == 0
    assert LegacyObjectMap.objects.count() == 13

    first_topic = Topic.objects.get()
    assert first_topic.lesson_links.count() == 2
    assert LegacyAuditEntry.objects.get().revert_data == ""


@pytest.mark.django_db
def test_repeated_successful_import_reuses_result_without_duplicates(
    school: School,
    backup_bytes: bytes,
) -> None:
    importer = ArtJournalImporter()
    first_report = importer.import_bytes(backup_bytes, school=school)
    counts_after_first_import = domain_counts()

    second_report = importer.import_bytes(backup_bytes, school=school)

    assert second_report["batchId"] == first_report["batchId"]
    assert second_report["reused"] is True
    assert ImportBatch.objects.count() == 1
    assert domain_counts() == counts_after_first_import


@pytest.mark.django_db
def test_repeated_successful_import_does_not_revalidate_known_bytes(
    school: School,
    backup_bytes: bytes,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    importer = ArtJournalImporter()
    importer.import_bytes(backup_bytes, school=school)

    def fail_validation(_document: object) -> object:
        raise AssertionError("known successful bytes must not be revalidated")

    monkeypatch.setattr(
        "apps.imports.service.validate_backup_document",
        fail_validation,
    )

    report = importer.import_bytes(backup_bytes, school=school)

    assert report["reused"] is True


@pytest.mark.django_db
def test_same_export_id_with_different_content_is_conflict(
    school: School,
    backup_bytes: bytes,
    backup_document: dict[str, object],
) -> None:
    ArtJournalImporter().import_bytes(backup_bytes, school=school)
    changed = deepcopy(backup_document)
    changed["exportedAtEpochMillis"] += 1

    with pytest.raises(ImportConflictError) as captured:
        ArtJournalImporter().import_bytes(encode(changed), school=school)

    assert captured.value.report["status"] == "conflict"
    assert captured.value.report["errors"][0]["code"] == "export_id_checksum_conflict"
    assert ImportBatch.objects.count() == 1


@pytest.mark.django_db
def test_dry_run_does_not_write_database(
    school: School,
    backup_bytes: bytes,
) -> None:
    before = domain_counts()

    report = ArtJournalImporter().import_bytes(
        backup_bytes,
        school=school,
        dry_run=True,
    )

    assert report["status"] == "validated"
    assert report["dryRun"] is True
    assert ImportBatch.objects.count() == 0
    assert LegacyObjectMap.objects.count() == 0
    assert domain_counts() == before


@pytest.mark.django_db
@pytest.mark.parametrize(
    ("field", "value", "error_code"),
    [
        ("format", "other-backup", "unsupported_format"),
        ("formatVersion", 2, "unsupported_format_version"),
    ],
)
def test_unsupported_envelope_is_reported_and_batch_is_failed(
    school: School,
    backup_document: dict[str, object],
    field: str,
    value: object,
    error_code: str,
) -> None:
    changed = deepcopy(backup_document)
    changed[field] = value

    with pytest.raises(ImportValidationError) as captured:
        ArtJournalImporter().import_bytes(encode(changed), school=school)

    assert error_code in {error["code"] for error in captured.value.report["errors"]}
    assert ImportBatch.objects.get().status == ImportBatch.Status.FAILED
    assert not any(domain_counts().values())


@pytest.mark.django_db
def test_incomplete_json_is_rejected_without_domain_writes(
    school: School,
    backup_document: dict[str, object],
) -> None:
    changed = deepcopy(backup_document)
    del changed["data"]["topics"]

    with pytest.raises(ImportValidationError) as captured:
        ArtJournalImporter().import_bytes(encode(changed), school=school)

    assert "schema_error" in {error["code"] for error in captured.value.report["errors"]}
    assert not any(domain_counts().values())


@pytest.mark.django_db
def test_broken_reference_is_reported_before_transaction(
    school: School,
    backup_document: dict[str, object],
) -> None:
    changed = deepcopy(backup_document)
    changed["data"]["students"][0]["groupId"] = 999

    with pytest.raises(ImportValidationError) as captured:
        ArtJournalImporter().import_bytes(encode(changed), school=school)

    assert "missing_reference" in {error["code"] for error in captured.value.report["errors"]}
    assert ImportBatch.objects.get().status == ImportBatch.Status.FAILED
    assert LegacyObjectMap.objects.count() == 0
    assert not any(domain_counts().values())


@pytest.mark.django_db
def test_invalid_real_date_and_inverted_period_are_reported(
    school: School,
    backup_document: dict[str, object],
) -> None:
    changed = deepcopy(backup_document)
    changed["data"]["payments"][0]["date"] = "2026-02-31"
    changed["data"]["quarters"][0]["startDate"] = "2026-10-26"

    with pytest.raises(ImportValidationError) as captured:
        ArtJournalImporter().import_bytes(encode(changed), school=school)

    codes = {error["code"] for error in captured.value.report["errors"]}
    assert {"invalid_date", "inverted_date_range"} <= codes
    assert not any(domain_counts().values())


@pytest.mark.django_db
def test_failure_mid_import_rolls_back_every_domain_record(
    school: School,
    backup_bytes: bytes,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    importer = ArtJournalImporter()

    def fail_payments(*_args: object, **_kwargs: object) -> None:
        raise RuntimeError("simulated failure")

    monkeypatch.setattr(importer, "_import_payments", fail_payments)

    with pytest.raises(ArtJournalImportError) as captured:
        importer.import_bytes(backup_bytes, school=school)

    assert captured.value.report["errors"][-1]["code"] == "import_aborted"
    batch = ImportBatch.objects.get()
    assert batch.status == ImportBatch.Status.FAILED
    assert LegacyObjectMap.objects.count() == 0
    assert not any(domain_counts().values())


@pytest.mark.django_db
def test_same_file_can_retry_after_transient_failed_import(
    school: School,
    backup_bytes: bytes,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    failing_importer = ArtJournalImporter()

    def fail_payments(*_args: object, **_kwargs: object) -> None:
        raise RuntimeError("simulated failure")

    with monkeypatch.context() as patch:
        patch.setattr(failing_importer, "_import_payments", fail_payments)
        with pytest.raises(ArtJournalImportError):
            failing_importer.import_bytes(backup_bytes, school=school)

    report = ArtJournalImporter().import_bytes(backup_bytes, school=school)

    assert report["status"] == "succeeded"
    assert report["reused"] is False
    assert ImportBatch.objects.count() == 1
    assert ImportBatch.objects.get().status == ImportBatch.Status.SUCCEEDED
    assert Student.objects.count() == 1


@pytest.mark.django_db
def test_same_export_cannot_be_reused_for_another_school(
    school: School,
    backup_bytes: bytes,
) -> None:
    other_school = School.objects.create(
        name="Other school",
        slug="other-school",
        default_currency="RUB",
    )
    ArtJournalImporter().import_bytes(backup_bytes, school=school)

    with pytest.raises(ImportConflictError):
        ArtJournalImporter().import_bytes(backup_bytes, school=other_school)
