package core.ui.calendar

import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TIME_SKELETON_12_HOUR: String = "hm"
private const val TIME_SKELETON_24_HOUR: String = "Hm"
internal const val HOUR_PATTERN_12_HOUR: String = "h"
internal const val HOUR_PATTERN_24_HOUR: String = "H"
private val DAY_PERIOD_REGEX: Regex = Regex("[\\s\\u00A0\\u202F]*[aBb]+[\\s\\u00A0\\u202F]*")
private val HOUR_REGEX: Regex = Regex("[hHK]+")

/** Formats clock and date text with the locale's best patterns. */
class ClockTextFormatter(private val datePatternSource: IDatePatternSource) {
    fun format(dateTime: ZonedDateTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(dateTime)

    fun timePattern(reading: ClockReading, is24Hour: Boolean): String {
        val skeleton: String = if (is24Hour) TIME_SKELETON_24_HOUR else TIME_SKELETON_12_HOUR
        return datePatternSource.bestPattern(skeleton, reading.locale)
    }

    fun datePattern(skeleton: String, reading: ClockReading): String = datePatternSource.bestPattern(skeleton, reading.locale)

    fun displayPattern(reading: ClockReading, is24Hour: Boolean, showAmPm: Boolean): String {
        val pattern: String = timePattern(reading = reading, is24Hour = is24Hour)
        if (is24Hour) {
            return pattern
        }
        val twelveHour: String = pattern.replace(HOUR_REGEX, HOUR_PATTERN_12_HOUR)
        return if (showAmPm) twelveHour else twelveHour.replace(DAY_PERIOD_REGEX, "").trim()
    }
}
