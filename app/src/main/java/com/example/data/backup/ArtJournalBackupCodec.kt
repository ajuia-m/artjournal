package com.example.data.backup

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException

class ArtJournalBackupCodec(
    moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
) {
    private val adapter = moshi.adapter(ArtJournalBackupV1::class.java).indent("  ")

    fun encode(backup: ArtJournalBackupV1): String = adapter.toJson(backup)

    fun decode(json: String): ArtJournalBackupV1 {
        val backup = try {
            adapter.fromJson(json)
        } catch (exception: IOException) {
            throw InvalidArtJournalBackupException("Не удалось прочитать JSON", exception)
        } catch (exception: JsonDataException) {
            throw InvalidArtJournalBackupException("JSON не соответствует формату Art Journal", exception)
        } ?: throw InvalidArtJournalBackupException("JSON не содержит резервную копию")

        if (backup.format != ART_JOURNAL_BACKUP_FORMAT) {
            throw InvalidArtJournalBackupException(
                "Неизвестный формат резервной копии: ${backup.format}"
            )
        }
        if (backup.formatVersion != ART_JOURNAL_BACKUP_FORMAT_VERSION) {
            throw InvalidArtJournalBackupException(
                "Неподдерживаемая версия резервной копии: ${backup.formatVersion}"
            )
        }

        return backup
    }
}

class InvalidArtJournalBackupException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
