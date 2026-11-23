package bd.nib.songbadpatro.presentation.util

import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.model.AppLanguage
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

object BanglaFormatter {

    private val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    private val englishDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val englishShortDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    private val banglaMonths = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    fun toBanglaDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(banglaDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatCount(value: Int, language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) value.toString() else toBanglaDigits(value.toString())

    fun formatDhakaDate(epochMillis: Long, language: AppLanguage = AppLanguage.BANGLA, includeTime: Boolean = false): String {
        if (epochMillis <= 0L) {
            return if (language == AppLanguage.ENGLISH) "Unknown date" else "অজানা তারিখ"
        }
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(PublicationCutoffConfig.DHAKA_ZONE)
        return if (language == AppLanguage.ENGLISH) {
            if (includeTime) zdt.format(englishDateFormatter) else zdt.format(englishShortDateFormatter)
        } else {
            val day = toBanglaDigits(zdt.dayOfMonth.toString())
            val month = banglaMonths[zdt.monthValue - 1]
            val year = toBanglaDigits(zdt.year.toString())
            if (!includeTime) {
                "$day $month $year"
            } else {
                val hour24 = zdt.hour
                val period = when (hour24) {
                    in 0..4 -> "রাত"
                    in 5..11 -> "সকাল"
                    in 12..15 -> "দুপুর"
                    in 16..18 -> "বিকাল"
                    in 19..20 -> "সন্ধ্যা"
                    else -> "রাত"
                }
                val hour12 = when {
                    hour24 == 0 -> 12
                    hour24 > 12 -> hour24 - 12
                    else -> hour24
                }
                val min = zdt.minute.toString().padStart(2, '0')
                "$day $month $year, $period ${toBanglaDigits("$hour12:$min")}"
            }
        }
    }

    fun formatRelativeOrDate(
        epochMillis: Long,
        language: AppLanguage = AppLanguage.BANGLA,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val diffMs = (nowMillis - epochMillis).coerceAtLeast(0L)
        val minutes = diffMs / 60_000L
        val hours = minutes / 60L

        return if (language == AppLanguage.ENGLISH) {
            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes min ago"
                hours < 24 -> "$hours hr ago"
                else -> formatDhakaDate(epochMillis, language, includeTime = false)
            }
        } else {
            when {
                minutes < 1 -> "এইমাত্র"
                minutes < 60 -> "${toBanglaDigits(minutes.toString())} মিনিট আগে"
                hours < 24 -> "${toBanglaDigits(hours.toString())} ঘণ্টা আগে"
                else -> formatDhakaDate(epochMillis, language, includeTime = false)
            }
        }
    }

    fun formatHour24(hour24: Int, language: AppLanguage = AppLanguage.BANGLA): String {
        val h = hour24.coerceIn(0, 23)
        val hour12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return if (language == AppLanguage.ENGLISH) {
            val suffix = if (h < 12) "AM" else "PM"
            "$hour12:00 $suffix"
        } else {
            val period = when (h) {
                in 0..4 -> "রাত"
                in 5..11 -> "সকাল"
                in 12..15 -> "দুপুর"
                in 16..18 -> "বিকাল"
                in 19..20 -> "সন্ধ্যা"
                else -> "রাত"
            }
            "$period ${toBanglaDigits(hour12.toString())}:০০"
        }
    }
}
