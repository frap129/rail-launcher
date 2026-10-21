package feature.widgets

import core.data.battery.BatteryStatus
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderSettings
import core.data.prefs.is24Hour
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val TIME_SKELETON_12_HOUR: String = "hm"
private const val TIME_SKELETON_24_HOUR: String = "Hm"
private const val HOUR_PATTERN_12_HOUR: String = "h"
private const val HOUR_PATTERN_24_HOUR: String = "H"
private const val MINUTE_PATTERN: String = "mm"
private const val DAY_PERIOD_PATTERN: String = "a"
private const val SPOKEN_TIME_PATTERN_12_HOUR: String = "h:mm a"
private const val SPOKEN_TIME_PATTERN_24_HOUR: String = "HH:mm"
private const val PREVIEW_ID: Long = 0L
private const val LOW_BATTERY_PERCENT: Int = 20
private val DAY_PERIOD_REGEX: Regex = Regex("[\\s\\u00A0\\u202F]*[aBb]+[\\s\\u00A0\\u202F]*")
private val HOUR_REGEX: Regex = Regex("[hHK]+")

/** Pure mapping from Header settings and the current clock reading to the values the Header renders. */
class HeaderFormatter(private val datePatternSource: IDatePatternSource) {
    fun format(id: Long, reading: ClockReading, settings: HeaderSettings, battery: BatteryStatus? = null): HeaderModel {
        val dateTime: ZonedDateTime = reading.instant.atZone(reading.zoneId)
        val is24Hour: Boolean = settings.timeFormat.is24Hour(reading.isSystem24Hour)
        return HeaderModel(
            id = id,
            time = if (settings.clockVisible) {
                createTimeModel(
                    dateTime = dateTime,
                    reading = reading,
                    is24Hour = is24Hour,
                    showAmPm = !is24Hour && settings.showAmPm,
                    style = settings.clockStyle
                )
            } else {
                null
            },
            date = if (settings.dateVisible) createDateModel(dateTime, reading, settings.dateFormat) else null,
            battery = createBatteryModel(battery, settings.batteryVisibility),
            style = settings.clockStyle,
            alignment = settings.alignment,
            size = settings.size
        )
    }

    fun formatDate(reading: ClockReading, preset: DateFormatPreset): String {
        val dateTime: ZonedDateTime = reading.instant.atZone(reading.zoneId)
        return format(dateTime, datePatternSource.bestPattern(preset.skeleton, reading.locale), reading.locale)
    }

    fun previews(reading: ClockReading, settings: HeaderSettings): List<HeaderModel> = ClockStyle.entries.map { style ->
        format(id = PREVIEW_ID, reading = reading, settings = settings.copy(clockStyle = style))
    }

    private fun createTimeModel(
        dateTime: ZonedDateTime,
        reading: ClockReading,
        is24Hour: Boolean,
        showAmPm: Boolean,
        style: ClockStyle
    ): HeaderTimeModel {
        val spokenText: String = format(dateTime, spokenPattern(is24Hour), reading.locale)
        val common: HeaderTimeModel = HeaderTimeModel(
            text = null,
            hourText = null,
            minuteText = null,
            amPmText = null,
            spokenText = spokenText,
            tapTarget = HeaderTapTarget.SystemAlarms,
            clickLabel = R.string.open_alarms
        )
        return if (style == ClockStyle.STACKED) {
            common.copy(
                hourText = format(dateTime, if (is24Hour) HOUR_PATTERN_24_HOUR else HOUR_PATTERN_12_HOUR, reading.locale),
                minuteText = format(dateTime, MINUTE_PATTERN, reading.locale),
                amPmText = if (showAmPm) format(dateTime, DAY_PERIOD_PATTERN, reading.locale) else null
            )
        } else {
            common.copy(text = format(dateTime, displayPattern(reading, is24Hour, showAmPm), reading.locale))
        }
    }

    private fun createDateModel(dateTime: ZonedDateTime, reading: ClockReading, preset: DateFormatPreset): HeaderLineModel =
        HeaderLineModel(
            text = format(dateTime, datePatternSource.bestPattern(preset.skeleton, reading.locale), reading.locale),
            spokenText = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(reading.locale).format(dateTime),
            tapTarget = HeaderTapTarget.SystemCalendar(atEpochMillis = reading.instant.toEpochMilli()),
            clickLabel = R.string.open_calendar
        )

    private fun createBatteryModel(status: BatteryStatus?, visibility: BatteryVisibility): BatteryModel? {
        if (status == null) {
            return null
        }
        val isLow: Boolean = status.percent <= LOW_BATTERY_PERCENT
        val isVisible: Boolean = when (visibility) {
            BatteryVisibility.NEVER -> false
            BatteryVisibility.ALWAYS -> true
            BatteryVisibility.CHARGING_OR_LOW -> status.isCharging || isLow
        }
        if (!isVisible) {
            return null
        }
        return BatteryModel(
            text = "${status.percent}%",
            percent = status.percent,
            isCharging = status.isCharging,
            isLow = isLow && !status.isCharging
        )
    }

    private fun displayPattern(reading: ClockReading, is24Hour: Boolean, showAmPm: Boolean): String {
        val skeleton: String = if (is24Hour) TIME_SKELETON_24_HOUR else TIME_SKELETON_12_HOUR
        val pattern: String = datePatternSource.bestPattern(skeleton, reading.locale)
        if (is24Hour) {
            return pattern
        }
        val twelveHour: String = pattern.replace(HOUR_REGEX, HOUR_PATTERN_12_HOUR)
        return if (showAmPm) twelveHour else twelveHour.replace(DAY_PERIOD_REGEX, "").trim()
    }

    private fun spokenPattern(is24Hour: Boolean): String = if (is24Hour) SPOKEN_TIME_PATTERN_24_HOUR else SPOKEN_TIME_PATTERN_12_HOUR

    private fun format(dateTime: ZonedDateTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(dateTime)
}
