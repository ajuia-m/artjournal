import json
from pathlib import Path
from typing import Any

from django.core.management.base import BaseCommand, CommandError, CommandParser

from apps.imports.exceptions import ArtJournalImportError
from apps.imports.service import ArtJournalImporter
from apps.schools.models import School


class Command(BaseCommand):
    help = "Validate and import an Art Journal JSON v1 backup."

    def add_arguments(self, parser: CommandParser) -> None:
        parser.add_argument("backup", type=Path)
        parser.add_argument(
            "--school",
            required=True,
            help="UUID of the target school.",
        )
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Validate and report without writing anything to the database.",
        )

    def handle(self, *args: Any, **options: Any) -> None:
        backup_path: Path = options["backup"]
        try:
            raw_bytes = backup_path.read_bytes()
        except OSError as exception:
            raise CommandError(f"Cannot read backup file: {backup_path}") from exception

        try:
            school = School.objects.get(pk=options["school"])
        except (School.DoesNotExist, ValueError) as exception:
            raise CommandError("Target school does not exist.") from exception

        try:
            report = ArtJournalImporter().import_bytes(
                raw_bytes,
                school=school,
                dry_run=options["dry_run"],
            )
        except ArtJournalImportError as exception:
            self.stderr.write(json.dumps(exception.report, ensure_ascii=False, indent=2))
            raise CommandError(str(exception)) from exception

        self.stdout.write(json.dumps(report, ensure_ascii=False, indent=2))
        if options["dry_run"]:
            self.stdout.write(self.style.SUCCESS("Dry run completed; database was not changed."))
        elif report["reused"]:
            self.stdout.write(self.style.SUCCESS("Backup was already imported; result reused."))
        else:
            self.stdout.write(self.style.SUCCESS("Backup imported successfully."))
