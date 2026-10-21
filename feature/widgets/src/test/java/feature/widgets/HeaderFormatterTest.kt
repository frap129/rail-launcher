package feature.widgets

import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.TimeFormat
import core.data.testing.FakeDatePatternSource
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderFormatterTest {
    private val reading: ClockReading = reading()

    @Test
    fun `formats each time format against the system 24-hour flag`() {
        assertEquals("2:05", time(HeaderSettings(timeFormat = TimeFormat.SYSTEM), isSystem24Hour = false))
        assertEquals("14:05", time(HeaderSettings(timeFormat = TimeFormat.SYSTEM), isSystem24Hour = true))
        assertEquals("2:05", time(HeaderSettings(timeFormat = TimeFormat.H12), isSystem24Hour = true))
        assertEquals("14:05", time(HeaderSettings(timeFormat = TimeFormat.H24), isSystem24Hour = false))
    }

    @Test
    fun `shows AM PM only in effective 12-hour time`() {
        val twelveHour: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = true))
        assertEquals("2:05 PM", twelveHour.time?.text)
        assertEquals("2:05 PM", twelveHour.time?.spokenText)
        assertNull(twelveHour.time?.amPmText)

        val twentyFourHour: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H24, showAmPm = true))
        assertEquals("14:05", twentyFourHour.time?.text)
        assertEquals("14:05", twentyFourHour.time?.spokenText)

        val systemTwentyFourHour: HeaderModel = format(
            HeaderSettings(timeFormat = TimeFormat.SYSTEM, showAmPm = true),
            isSystem24Hour = true
        )
        assertEquals("14:05", systemTwentyFourHour.time?.text)

        val twelveHourWithoutMarker: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = false))
        assertEquals("2:05", twelveHourWithoutMarker.time?.text)
        assertEquals("2:05 PM", twelveHourWithoutMarker.time?.spokenText)
    }

    @Test
    fun `splits stacked time into hours minutes and AM PM`() {
        val twelveHour: HeaderModel = format(
            HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H12, showAmPm = true)
        )
        assertNull(twelveHour.time?.text)
        assertEquals("2", twelveHour.time?.hourText)
        assertEquals("05", twelveHour.time?.minuteText)
        assertEquals("PM", twelveHour.time?.amPmText)
        assertEquals("2:05 PM", twelveHour.time?.spokenText)

        val twentyFourHour: HeaderModel = format(
            HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H24, showAmPm = true)
        )
        assertEquals("14", twentyFourHour.time?.hourText)
        assertEquals("05", twentyFourHour.time?.minuteText)
        assertNull(twentyFourHour.time?.amPmText)
    }

    @Test
    fun `does not leak a locale day period prefix into stacked hours`() {
        val formatter: HeaderFormatter = HeaderFormatter(
            FakeDatePatternSource(mapOf("hm" to "a h:mm", "Hm" to "H:mm", "EEEEMMMMd" to "EEEE, MMMM d"))
        )
        val header: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H12, showAmPm = true)
        )
        assertEquals("2", header.time?.hourText)
        assertEquals("05", header.time?.minuteText)
        assertEquals("오후", header.time?.amPmText)
    }

    @Test
    fun `keeps the locale day period position in 12-hour display`() {
        val formatter: HeaderFormatter = HeaderFormatter(
            FakeDatePatternSource(mapOf("hm" to "a h:mm", "Hm" to "H:mm", "EEEEMMMMd" to "EEEE, MMMM d"))
        )
        val withMarker: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = true)
        )
        val withoutMarker: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = false)
        )
        assertEquals("오후 2:05", withMarker.time?.text)
        assertEquals("2:05", withoutMarker.time?.text)
    }

    @Test
    fun `requests and formats every date preset skeleton`() {
        val skeletonSource: RecordingDatePatternSource = RecordingDatePatternSource()
        val formatter: HeaderFormatter = HeaderFormatter(skeletonSource)
        DateFormatPreset.entries.forEach { preset -> formatter.formatDate(reading, preset) }
        assertEquals(DateFormatPreset.entries.map { preset -> preset.skeleton }, skeletonSource.requestedSkeletons)
        assertEquals(
            listOf("Tuesday, October 20", "Tue, Oct 20", "October 20", "Tuesday 20", "Tuesday", "10/20/2026"),
            DateFormatPreset.entries.map { preset -> formatter.formatDate(reading, preset) }
        )
    }

    @Test
    fun `carries the selected style alignment and size into the model`() {
        val header: HeaderModel = format(
            HeaderSettings(
                clockStyle = ClockStyle.COMPACT,
                dateFormat = DateFormatPreset.WEEKDAY,
                alignment = HeaderAlignment.CENTER,
                size = HeaderSize.LARGE
            )
        )
        assertEquals(1L, header.id)
        assertEquals(ClockStyle.COMPACT, header.style)
        assertEquals(HeaderAlignment.CENTER, header.alignment)
        assertEquals(HeaderSize.LARGE, header.size)
        assertEquals("2:05", header.time?.text)
        assertEquals("Tuesday", header.date?.text)
    }

    private fun format(settings: HeaderSettings, isSystem24Hour: Boolean = false): HeaderModel =
        HeaderFormatter(FakeDatePatternSource()).format(
            id = 1L,
            reading = reading(isSystem24Hour = isSystem24Hour),
            settings = settings
        )

    private fun time(settings: HeaderSettings, isSystem24Hour: Boolean): String? = format(settings, isSystem24Hour).time?.text

    private fun reading(locale: Locale = Locale.US, isSystem24Hour: Boolean = false): ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = locale,
        isSystem24Hour = isSystem24Hour
    )

    private class RecordingDatePatternSource : IDatePatternSource {
        val requestedSkeletons: MutableList<String> = mutableListOf()

        override fun bestPattern(skeleton: String, locale: Locale): String {
            requestedSkeletons.add(skeleton)
            return when (skeleton) {
                "EEEEMMMMd" -> "EEEE, MMMM d"
                "EEEMMMd" -> "EEE, MMM d"
                "MMMMd" -> "MMMM d"
                "EEEEd" -> "EEEE d"
                "EEEE" -> "EEEE"
                "yMd" -> "M/d/y"
                else -> skeleton
            }
        }
    }
}
