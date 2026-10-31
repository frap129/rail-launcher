package core.ui.widgets

import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.profiles.ProfileToken
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.packageLevelKey
import core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IHeaderTapTargetResolverImplTest {
    private val resolver: IHeaderTapTargetResolver = IHeaderTapTargetResolverImpl()
    private val nowMillis: Long = 1_700_000_000_000L
    private val inputLaunchable: RailLaunchable = RailLaunchable(
        defaultName = "Maps",
        defaultIcon = Icon.ApplicationIcon(packageName = "com.example.maps"),
        deeplink = "rail://maps"
    )
    private val inputFolder: Folder = Folder(folderId = 1L)

    @Test
    fun `applies the system defaults when no tap action is set`() {
        val inputHeader: HeaderModel = header()
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = HeaderSettings(),
            launchableState = createLaunchableState(groups = emptyList()),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
        assertEquals(R.string.open_alarms, actualHeader.time?.clickLabel)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actualHeader.date?.tapTarget)
        assertEquals(R.string.open_calendar, actualHeader.date?.clickLabel)
    }

    @Test
    fun `applies Nothing to both Header lines`() {
        val inputHeader: HeaderModel = header()
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = HeaderTapSetting.Nothing),
            launchableState = createLaunchableState(groups = emptyList()),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.None, actualHeader.time?.tapTarget)
        assertNull(actualHeader.time?.clickLabel)
        assertEquals(HeaderTapTarget.None, actualHeader.date?.tapTarget)
        assertNull(actualHeader.date?.clickLabel)
    }

    @Test
    fun `applies a chosen Launchable with its click labels`() {
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key),
            dateTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key)
        )
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchableState = createLoadedLaunchableState(listOf(inputLaunchable)),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable), actualHeader.time?.tapTarget)
        assertEquals(R.string.open_launchable, actualHeader.time?.clickLabel)
        assertEquals("Maps", actualHeader.time?.clickLabelArg)
        assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable), actualHeader.date?.tapTarget)
        assertEquals("Maps", actualHeader.date?.clickLabelArg)
    }

    @Test
    fun `falls back to the system default for a package-level key`() {
        val inputApp = createApp(name = "Maps", packageName = "com.example.maps")
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = inputApp.packageLevelKey())
        )

        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchableState = createLoadedLaunchableState(listOf(inputApp)),
            atEpochMillis = nowMillis
        )

        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
    }

    @Test
    fun `falls back to the system default for a removed Launchable`() {
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = "rail://uninstalled"),
            dateTapTarget = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        )
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchableState = createLoadedLaunchableState(listOf(inputLaunchable)),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actualHeader.date?.tapTarget)
    }

    @Test
    fun `falls back to the system default for a Folder`() {
        val inputHeader: HeaderModel = header()
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = inputFolder.key),
            dateTapTarget = HeaderTapSetting.Launchable(key = inputFolder.key)
        )
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchableState = createLaunchableState(groups = emptyList(), folders = listOf(inputFolder)),
            atEpochMillis = nowMillis
        )
        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actualHeader.date?.tapTarget)
    }

    @Test
    fun `resolves a stored Folder to the system default once loaded`() {
        val actualSetting: HeaderTapSetting = resolver.resolveSetting(
            setting = HeaderTapSetting.Launchable(key = inputFolder.key),
            launchableState = createLaunchableState(groups = emptyList(), folders = listOf(inputFolder))
        )
        assertEquals(HeaderTapSetting.SystemDefault, actualSetting)
    }

    @Test
    fun `resolves a removed Launchable to the system default once loaded`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        val actualSetting: HeaderTapSetting = resolver.resolveSetting(
            setting = inputSetting,
            launchableState = createLoadedLaunchableState(listOf(inputLaunchable))
        )
        assertEquals(HeaderTapSetting.SystemDefault, actualSetting)
    }

    @Test
    fun `keeps a stored Launchable while Launchables are not loaded`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        val actualSetting: HeaderTapSetting = resolver.resolveSetting(
            setting = inputSetting,
            launchableState = createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        assertEquals(inputSetting, actualSetting)
    }

    @Test
    fun `resolves a present Launchable, Nothing and the system default unchanged`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputLaunchable.key)
        val actualLaunchable: HeaderTapSetting =
            resolver.resolveSetting(setting = inputSetting, launchableState = createLoadedLaunchableState(listOf(inputLaunchable)))
        val actualNothing: HeaderTapSetting =
            resolver.resolveSetting(setting = HeaderTapSetting.Nothing, launchableState = createLaunchableState(groups = emptyList()))
        val actualDefault: HeaderTapSetting =
            resolver.resolveSetting(setting = HeaderTapSetting.SystemDefault, launchableState = createLaunchableState(groups = emptyList()))
        assertEquals(inputSetting, actualLaunchable)
        assertEquals(HeaderTapSetting.Nothing, actualNothing)
        assertEquals(HeaderTapSetting.SystemDefault, actualDefault)
    }

    @Test
    fun `falls back to the system default for a Locked private App`() {
        val inputKey: String = "com.example.secret_UserHandle{11}"
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = inputKey),
            dateTapTarget = HeaderTapSetting.Launchable(key = inputKey)
        )
        val inputState = createLaunchableState(
            groups = emptyList(),
            keptKeys = setOf(ProfileToken("UserHandle{11}"))
        )
        val inputHeader: HeaderModel = header()
        val actualSetting: HeaderTapSetting = resolver.resolveSetting(
            setting = HeaderTapSetting.Launchable(key = inputKey),
            launchableState = inputState
        )
        val actualHeader: HeaderModel = resolver.applyTo(
            header = inputHeader,
            settings = inputSettings,
            launchableState = inputState,
            atEpochMillis = nowMillis
        )

        assertEquals(HeaderTapSetting.SystemDefault, actualSetting)
        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actualHeader.date?.tapTarget)
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
