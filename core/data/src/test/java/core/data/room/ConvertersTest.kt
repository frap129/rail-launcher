package core.data.room

import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters: Converters = Converters()

    @Test
    fun `round trips Header settings enums by name`() {
        ClockStyle.entries.forEach { style ->
            assertEquals(style, converters.fromStringToClockStyle(converters.fromClockStyleToString(style)))
        }
        TimeFormat.entries.forEach { format ->
            assertEquals(format, converters.fromStringToTimeFormat(converters.fromTimeFormatToString(format)))
        }
        DateFormatPreset.entries.forEach { format ->
            assertEquals(format, converters.fromStringToDateFormatPreset(converters.fromDateFormatPresetToString(format)))
        }
        HeaderAlignment.entries.forEach { alignment ->
            assertEquals(alignment, converters.fromStringToHeaderAlignment(converters.fromHeaderAlignmentToString(alignment)))
        }
        HeaderSize.entries.forEach { size ->
            assertEquals(size, converters.fromStringToHeaderSize(converters.fromHeaderSizeToString(size)))
        }
    }

    @Test
    fun `falls back to defaults for unknown Header settings enum names`() {
        assertEquals(ClockStyle.CLASSIC, converters.fromStringToClockStyle("UNKNOWN"))
        assertEquals(TimeFormat.SYSTEM, converters.fromStringToTimeFormat("UNKNOWN"))
        assertEquals(DateFormatPreset.FULL, converters.fromStringToDateFormatPreset("UNKNOWN"))
        assertEquals(HeaderAlignment.START, converters.fromStringToHeaderAlignment("UNKNOWN"))
        assertEquals(HeaderSize.DEFAULT, converters.fromStringToHeaderSize("UNKNOWN"))
    }

    @Test
    fun `encodes the system default as a null Header tap target`() {
        assertNull(converters.fromHeaderTapSettingToString(HeaderTapSetting.SystemDefault))
        assertNull(converters.fromHeaderTapSettingToString(null))
        assertEquals(HeaderTapSetting.SystemDefault, converters.fromStringToHeaderTapSetting(null))
    }

    @Test
    fun `round trips every Header tap setting encoding`() {
        val settings: List<HeaderTapSetting> = listOf(
            HeaderTapSetting.Nothing,
            HeaderTapSetting.Launchable(key = "com.example.app_UserHandle{0}"),
            HeaderTapSetting.Launchable(key = "launchable:com.example/thing:extra")
        )
        assertEquals("none", converters.fromHeaderTapSettingToString(HeaderTapSetting.Nothing))
        settings.forEach { setting ->
            assertEquals(setting, converters.fromStringToHeaderTapSetting(converters.fromHeaderTapSettingToString(setting)))
        }
    }

    @Test
    fun `falls back to the system default for an unknown Header tap target`() {
        assertEquals(HeaderTapSetting.SystemDefault, converters.fromStringToHeaderTapSetting("unexpected"))
    }
}
