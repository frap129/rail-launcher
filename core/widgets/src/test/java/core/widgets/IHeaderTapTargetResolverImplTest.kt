package core.widgets

import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IHeaderTapTargetResolverImplTest {
    private val resolver: IHeaderTapTargetResolver = IHeaderTapTargetResolverImpl()
    private val nowMillis: Long = 1_700_000_000_000L
    private val mockLaunchable: RailLaunchable = RailLaunchable(
        defaultName = "Maps",
        defaultIcon = Icon.ApplicationIcon(packageName = "com.example.maps"),
        deeplink = "rail://maps"
    )

    @Test
    fun `applies the system defaults when no tap action is set`() {
        val inputHeader: HeaderModel = header()
        val actual: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = HeaderSettings(),
            launchables = emptyList(),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.SystemAlarms, actual.time?.tapTarget)
        assertEquals(R.string.open_alarms, actual.time?.clickLabel)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actual.date?.tapTarget)
        assertEquals(R.string.open_calendar, actual.date?.clickLabel)
    }

    @Test
    fun `applies Nothing to both Header lines`() {
        val inputHeader: HeaderModel = header()
        val actual: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = HeaderTapSetting.Nothing),
            launchables = emptyList(),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.None, actual.time?.tapTarget)
        assertNull(actual.time?.clickLabel)
        assertEquals(HeaderTapTarget.None, actual.date?.tapTarget)
        assertNull(actual.date?.clickLabel)
    }

    @Test
    fun `applies a chosen Launchable with its click labels`() {
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = mockLaunchable.key),
            dateTapTarget = HeaderTapSetting.Launchable(key = mockLaunchable.key)
        )
        val actual: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchables = listOf<Launchable>(mockLaunchable),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = mockLaunchable), actual.time?.tapTarget)
        assertEquals(R.string.open_launchable, actual.time?.clickLabel)
        assertEquals("Maps", actual.time?.clickLabelArg)
        assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = mockLaunchable), actual.date?.tapTarget)
        assertEquals("Maps", actual.date?.clickLabelArg)
    }

    @Test
    fun `falls back to the system default for a removed Launchable`() {
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = "rail://uninstalled"),
            dateTapTarget = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        )
        val actual: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchables = listOf<Launchable>(mockLaunchable),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.SystemAlarms, actual.time?.tapTarget)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actual.date?.tapTarget)
    }

    @Test
    fun `resolves a removed Launchable to the system default once loaded`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        val actual: HeaderTapSetting = resolver.resolveSetting(
            setting = inputSetting,
            launchables = listOf<Launchable>(mockLaunchable),
            isLoaded = true
        )
        assertEquals(HeaderTapSetting.SystemDefault, actual)
    }

    @Test
    fun `keeps a stored Launchable while Launchables are not loaded`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        val actual: HeaderTapSetting = resolver.resolveSetting(
            setting = inputSetting,
            launchables = emptyList(),
            isLoaded = false
        )
        assertEquals(inputSetting, actual)
    }

    @Test
    fun `resolves a present Launchable, Nothing and the system default unchanged`() {
        val inputLaunchable: HeaderTapSetting = HeaderTapSetting.Launchable(key = mockLaunchable.key)
        val actualLaunchable: HeaderTapSetting =
            resolver.resolveSetting(setting = inputLaunchable, launchables = listOf<Launchable>(mockLaunchable), isLoaded = true)
        val actualNothing: HeaderTapSetting =
            resolver.resolveSetting(setting = HeaderTapSetting.Nothing, launchables = emptyList(), isLoaded = true)
        val actualDefault: HeaderTapSetting =
            resolver.resolveSetting(setting = HeaderTapSetting.SystemDefault, launchables = emptyList(), isLoaded = true)
        assertEquals(inputLaunchable, actualLaunchable)
        assertEquals(HeaderTapSetting.Nothing, actualNothing)
        assertEquals(HeaderTapSetting.SystemDefault, actualDefault)
    }

    private fun header(): HeaderModel = HeaderModel(
        id = 1L,
        time = HeaderTimeModel(
            text = "2:05",
            hourText = null,
            minuteText = null,
            amPmText = null,
            spokenText = "2:05 PM",
            tapTarget = HeaderTapTarget.SystemAlarms,
            clickLabel = R.string.open_alarms
        ),
        date = HeaderLineModel(
            text = "Tuesday, October 20",
            spokenText = "Tuesday, October 20, 2026",
            tapTarget = HeaderTapTarget.SystemCalendar(atEpochMillis = 0L),
            clickLabel = R.string.open_calendar
        ),
        battery = null,
        events = emptyList(),
        style = ClockStyle.CLASSIC,
        alignment = HeaderAlignment.START,
        size = HeaderSize.DEFAULT
    )
}
