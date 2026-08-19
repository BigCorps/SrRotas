package com.srrotas.app

import android.content.Context
import android.net.Uri
import android.media.ExifInterface
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object HistoricalImportTime {
    private val zone = ZoneId.of("America/Sao_Paulo")

    fun resolve(context: Context, uri: Uri, displayName: String): HistoricalSourceTime {
        queryLong(context, uri, MediaStore.Images.ImageColumns.DATE_TAKEN)
            ?.takeIf { it > 946684800000L }
            ?.let {
                return HistoricalSourceTime(
                    Instant.ofEpochMilli(it).toString(),
                    HistoricalTimeConfidence.METADATA_TAKEN,
                )
            }

        readExif(context, uri)?.let {
            return HistoricalSourceTime(it, HistoricalTimeConfidence.EXIF)
        }

        parseFilename(displayName)?.let {
            return HistoricalSourceTime(it, HistoricalTimeConfidence.FILENAME)
        }

        queryLong(context, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            ?.takeIf { it > 946684800000L }
            ?.let {
                return HistoricalSourceTime(
                    Instant.ofEpochMilli(it).toString(),
                    HistoricalTimeConfidence.LAST_MODIFIED,
                )
            }

        return HistoricalSourceTime(
            "2000-01-01T00:00:00Z",
            HistoricalTimeConfidence.UNKNOWN,
        )
    }

    @Suppress("DEPRECATION")
    private fun readExif(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return@use null
                val match = Regex(
                    """(20\d{2}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})""",
                ).find(raw) ?: return@use null
                toInstant(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].toInt(),
                    match.groupValues[6].toInt(),
                )
            }
        }.getOrNull()

    internal fun parseFilename(name: String): String? {
        val compact = Regex(
            """(?<!\d)(20\d{2})(\d{2})(\d{2})[-_ ]?(\d{2})(\d{2})(\d{2})(?!\d)""",
        ).find(name)
        compact?.let { m ->
            return toInstant(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt(),
            )
        }

        val separated = Regex(
            """(?<!\d)(20\d{2})[-_](\d{2})[-_](\d{2})[ T_-](\d{2})[-_:](\d{2})[-_:](\d{2})(?!\d)""",
        ).find(name)
        separated?.let { m ->
            return toInstant(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt(),
            )
        }
        return null
    }

    private fun toInstant(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int,
    ): String? = runCatching {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zone).toInstant().toString()
    }.getOrNull()

    private fun queryLong(context: Context, uri: Uri, column: String): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val i = c.getColumnIndex(column)
                if (i < 0 || c.isNull(i)) null else c.getLong(i)
            }
        }.getOrNull()
}
