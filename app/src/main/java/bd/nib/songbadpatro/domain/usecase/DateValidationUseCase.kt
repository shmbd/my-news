package bd.nib.songbadpatro.domain.usecase

import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class DateValidationOutcome {
    data class Eligible(
        val originalPublicationEpochMs: Long,
        val updatedEpochMs: Long?
    ) : DateValidationOutcome()

    data class PendingDateVerification(
        val reason: String,
        val updatedEpochMs: Long? = null
    ) : DateValidationOutcome()

    data class RejectedBeforeCutoff(
        val originalPublicationEpochMs: Long,
        val cutoffEpochMs: Long
    ) : DateValidationOutcome()

    data class RejectedInvalidOrMissing(
        val reason: String
    ) : DateValidationOutcome()
}

/**
 * Enforces the strict publication date cutoff (default: `2026-11-23` in `Asia/Dhaka`).
 *
 * Rules enforced:
 * 1. Uses ONLY the original publisher's publication timestamp (`originalPublishedRaw`),
 *    NEVER the aggregator discovery/import timestamp.
 * 2. Distinguishes `updatedRaw` from `originalPublishedRaw`:
 *    - If an article was originally published before `2026-11-23` and updated on/after `2026-11-23`,
 *      it is REJECTED unless `isExplicitlyNewlyPublished` is true AND its new publication date >= cutoff.
 *    - If `originalPublishedRaw` is missing while `updatedRaw` exists, it is NEVER auto-published;
 *      editorial candidates are placed in `PendingDateVerification`.
 * 3. Handles Bangladesh local time (`Asia/Dhaka`, `+06:00`), RFC-1123/822, ISO-8601,
 *    Bangla digits (`০-৯`), and Bangla month names.
 */
class DateValidationUseCase {

    fun validate(
        originalPublishedRaw: String?,
        updatedRaw: String? = null,
        cutoffIsoDate: String = PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE,
        isExplicitlyNewlyPublished: Boolean = false,
        allowPendingQueueWhenMissing: Boolean = true
    ): DateValidationOutcome {
        val cutoffEpochMs = PublicationCutoffConfig.cutoffEpochMillisFor(cutoffIsoDate)
        val parsedUpdated = updatedRaw?.takeIf { it.isNotBlank() }?.let { parseTimestampInDhaka(it) }

        if (originalPublishedRaw.isNullOrBlank()) {
            if (isExplicitlyNewlyPublished && parsedUpdated != null) {
                return if (parsedUpdated >= cutoffEpochMs) {
                    DateValidationOutcome.Eligible(parsedUpdated, parsedUpdated)
                } else {
                    DateValidationOutcome.RejectedBeforeCutoff(parsedUpdated, cutoffEpochMs)
                }
            }
            return if (allowPendingQueueWhenMissing) {
                DateValidationOutcome.PendingDateVerification(
                    reason = if (parsedUpdated != null) {
                        "মূল প্রকাশের তারিখ অনুপস্থিত (শুধু হালনাগাদের তারিখ পাওয়া গেছে)"
                    } else {
                        "প্রকাশকের মূল প্রকাশের তারিখ যাচাই করা যায়নি"
                    },
                    updatedEpochMs = parsedUpdated
                )
            } else {
                DateValidationOutcome.RejectedInvalidOrMissing("Missing original publication date")
            }
        }

        val parsedOriginal = parseTimestampInDhaka(originalPublishedRaw)
            ?: return DateValidationOutcome.RejectedInvalidOrMissing(
                "Invalid or unparseable publication date: $originalPublishedRaw"
            )

        if (parsedOriginal < cutoffEpochMs) {
            // Even if updated after 2026-11-23, reject older updated articles unless explicitly newly published
            if (isExplicitlyNewlyPublished && parsedUpdated != null && parsedUpdated >= cutoffEpochMs) {
                return DateValidationOutcome.Eligible(
                    originalPublicationEpochMs = parsedUpdated,
                    updatedEpochMs = parsedUpdated
                )
            }
            return DateValidationOutcome.RejectedBeforeCutoff(
                originalPublicationEpochMs = parsedOriginal,
                cutoffEpochMs = cutoffEpochMs
            )
        }

        return DateValidationOutcome.Eligible(
            originalPublicationEpochMs = parsedOriginal,
            updatedEpochMs = parsedUpdated
        )
    }

    companion object {
        private val banglaMonthToEnglish = mapOf(
            "জানুয়ারি" to "January",
            "জানুয়ারি" to "January",
            "ফেব্রুয়ারি" to "February",
            "ফেব্রুয়ারি" to "February",
            "মার্চ" to "March",
            "এপ্রিল" to "April",
            "মে" to "May",
            "জুন" to "June",
            "জুলাই" to "July",
            "আগস্ট" to "August",
            "সেপ্টেম্বর" to "September",
            "অক্টোবর" to "October",
            "নভেম্বর" to "November",
            "ডিসেম্বর" to "December"
        )

        private val zonedPatterns = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH)
        )

        private val localDateTimePatterns = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH)
        )

        private val localDatePatterns = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH)
        )

        fun parseTimestampInDhaka(rawInput: String?): Long? {
            if (rawInput.isNullOrBlank()) return null
            var normalized = normalizeBanglaDateText(rawInput.trim())
                .replace("GMT+06:00", "+0600")
                .replace("GMT+6", "+0600")
                .replace("UTC+6", "+0600")
                .replace(Regex("\\bBST\\b"), "+0600")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Strip leading Bangla/English prefixes like "প্রকাশ: " or "Published: "
            normalized = normalized
                .removePrefix("প্রকাশ:")
                .removePrefix("প্রকাশ :")
                .removePrefix("প্রকাশিত:")
                .removePrefix("Published:")
                .removePrefix("Published :")
                .trim()

            // 1. Standard ISO-8601 Instant / OffsetDateTime / ZonedDateTime
            runCatching { return validateYearBounds(Instant.parse(normalized).toEpochMilli()) }
            runCatching { return validateYearBounds(OffsetDateTime.parse(normalized).toInstant().toEpochMilli()) }
            runCatching { return validateYearBounds(ZonedDateTime.parse(normalized).toInstant().toEpochMilli()) }

            // 2. Zoned RFC-822 / custom offset patterns
            for (fmt in zonedPatterns) {
                runCatching {
                    val zdt = ZonedDateTime.parse(normalized, fmt)
                    return validateYearBounds(zdt.toInstant().toEpochMilli())
                }
            }

            // 3. Timezone-less LocalDateTime -> interpreted in Asia/Dhaka
            for (fmt in localDateTimePatterns) {
                runCatching {
                    val ldt = LocalDateTime.parse(normalized, fmt)
                    val epoch = ldt.atZone(PublicationCutoffConfig.DHAKA_ZONE).toInstant().toEpochMilli()
                    return validateYearBounds(epoch)
                }
            }

            // 4. Date-only LocalDate -> start of day in Asia/Dhaka
            for (fmt in localDatePatterns) {
                runCatching {
                    val ld = LocalDate.parse(normalized, fmt)
                    val epoch = ld.atStartOfDay(PublicationCutoffConfig.DHAKA_ZONE).toInstant().toEpochMilli()
                    return validateYearBounds(epoch)
                }
            }

            return null
        }

        private fun normalizeBanglaDateText(input: String): String {
            val asciiDigits = buildString(input.length) {
                for (ch in input) {
                    append(
                        when (ch) {
                            '০' -> '0'
                            '১' -> '1'
                            '২' -> '2'
                            '৩' -> '3'
                            '৪' -> '4'
                            '৫' -> '5'
                            '৬' -> '6'
                            '৭' -> '7'
                            '৮' -> '8'
                            '৯' -> '9'
                            else -> ch
                        }
                    )
                }
            }
            var result = asciiDigits
            for ((bn, en) in banglaMonthToEnglish) {
                if (result.contains(bn)) {
                    result = result.replace(bn, en)
                }
            }
            return result
        }

        private fun validateYearBounds(epochMs: Long): Long? {
            val year = Instant.ofEpochMilli(epochMs).atZone(PublicationCutoffConfig.DHAKA_ZONE).year
            return if (year in 2000..2100) epochMs else null
        }
    }
}
