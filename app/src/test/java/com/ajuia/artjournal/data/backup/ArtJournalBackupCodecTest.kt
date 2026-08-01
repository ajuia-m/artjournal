package com.ajuia.artjournal.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ArtJournalBackupCodecTest {

    private val codec = ArtJournalBackupCodec()

    @Test
    fun `rejects unsupported format version`() {
        val backup = ArtJournalBackupV1(
            exportId = "test-export",
            exportedAtEpochMillis = 1_700_000_000_000,
            source = ArtJournalBackupSourceV1(
                appVersionName = "1.0",
                appVersionCode = 1,
                roomSchemaVersion = 2
            ),
            data = emptyBackupData()
        )
        val unsupportedJson = codec.encode(backup)
            .replace("\"formatVersion\": 1", "\"formatVersion\": 99")

        try {
            codec.decode(unsupportedJson)
            fail("Unsupported backup version must be rejected")
        } catch (exception: InvalidArtJournalBackupException) {
            assertEquals(
                "Неподдерживаемая версия резервной копии: 99",
                exception.message
            )
        }
    }

    private fun emptyBackupData() = ArtJournalBackupDataV1(
        academicYears = emptyList(),
        groups = emptyList(),
        students = emptyList(),
        payments = emptyList(),
        quarters = emptyList(),
        lessons = emptyList(),
        studentLessonStates = emptyList(),
        topics = emptyList(),
        studentTopicProgress = emptyList(),
        auditLogs = emptyList()
    )
}
