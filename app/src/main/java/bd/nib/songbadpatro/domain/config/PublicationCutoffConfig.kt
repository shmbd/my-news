package bd.nib.songbadpatro.domain.config

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Centralized publication date cutoff configuration for My News (আমার খবর).
 *
 * Default cutoff: 2026-11-23T00:00:00+06:00 (Asia/Dhaka).
 * No article originally published before the configured cutoff date may enter
 * ingestion, storage, search, UI display, or background notifications.
 */
object PublicationCutoffConfig {

    const val DEFAULT_CUTOFF_ISO_DATE: String = "2026-11-23"
    const val TIMEZONE_ID: String = "Asia/Dhaka"

    val DHAKA_ZONE: ZoneId = ZoneId.of(TIMEZONE_ID)

    val DEFAULT_CUTOFF_LOCAL_DATE: LocalDate = LocalDate.of(2026, 11, 23)

    val DEFAULT_CUTOFF_EPOCH_MILLIS: Long = DEFAULT_CUTOFF_LOCAL_DATE
        .atStartOfDay(DHAKA_ZONE)
        .toInstant()
        .toEpochMilli()

    /**
     * Parses an administrative ISO date (yyyy-MM-dd) into start-of-day Asia/Dhaka epoch millis.
     * Falls back to [DEFAULT_CUTOFF_EPOCH_MILLIS] if blank or malformed.
     */
    fun cutoffEpochMillisFor(isoDate: String?): Long {
        if (isoDate.isNullOrBlank()) return DEFAULT_CUTOFF_EPOCH_MILLIS
        return runCatching {
            val parsed = LocalDate.parse(isoDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            parsed.atStartOfDay(DHAKA_ZONE).toInstant().toEpochMilli()
        }.getOrDefault(DEFAULT_CUTOFF_EPOCH_MILLIS)
    }

    fun isValidIsoCutoffDate(isoDate: String?): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return runCatching {
            LocalDate.parse(isoDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            true
        }.getOrDefault(false)
    }

    fun isOnOrAfterCutoff(
        originalPublicationEpochMillis: Long?,
        cutoffIsoDate: String = DEFAULT_CUTOFF_ISO_DATE
    ): Boolean {
        val pubMs = originalPublicationEpochMillis ?: return false
        if (pubMs <= 0L) return false
        return pubMs >= cutoffEpochMillisFor(cutoffIsoDate)
    }

    fun toDhakaZonedDateTime(epochMillis: Long): ZonedDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(DHAKA_ZONE)

    fun formatIsoDateInDhaka(epochMillis: Long): String =
        toDhakaZonedDateTime(epochMillis).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
}
