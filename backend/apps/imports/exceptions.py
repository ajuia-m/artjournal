class ArtJournalImportError(Exception):
    def __init__(self, message: str, report: dict[str, object]) -> None:
        super().__init__(message)
        self.report = report


class ImportValidationError(ArtJournalImportError):
    pass


class ImportConflictError(ArtJournalImportError):
    pass


class ImportInProgressError(ArtJournalImportError):
    pass
