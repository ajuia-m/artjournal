from io import StringIO
from pathlib import Path

import pytest
from django.core.management import call_command

from apps.imports.models import ImportBatch
from apps.schools.models import School


@pytest.mark.django_db
def test_management_command_dry_run_writes_report_without_database_changes(
    tmp_path: Path,
) -> None:
    school = School.objects.create(
        name="Import target",
        slug="import-target",
        default_currency="RUB",
    )
    source = (
        Path(__file__).resolve().parents[4]
        / "docs"
        / "examples"
        / "artjournal-backup-v1.example.json"
    )
    backup = tmp_path / "backup.json"
    backup.write_bytes(source.read_bytes())
    stdout = StringIO()

    call_command(
        "import_artjournal_backup",
        backup,
        school=str(school.id),
        dry_run=True,
        stdout=stdout,
    )

    assert '"status": "validated"' in stdout.getvalue()
    assert "database was not changed" in stdout.getvalue()
    assert ImportBatch.objects.count() == 0


def test_runtime_schema_matches_documented_schema() -> None:
    root = Path(__file__).resolve().parents[4]

    assert (
        root / "backend/apps/imports/schemas/artjournal-backup-v1.schema.json"
    ).read_bytes() == (root / "docs/schemas/artjournal-backup-v1.schema.json").read_bytes()
