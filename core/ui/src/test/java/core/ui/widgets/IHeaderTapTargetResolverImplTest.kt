package core.ui.widgets

import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.LaunchableReadiness
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.profiles.ProfileToken
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.packageLevelKey
import core.ui.R
import org.junit.Assert.assertEquals
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
        val actualHeader: HeaderModel = applyStyle(style = ClockStyle.CLASSIC)

        assertTimeDefault(header = actualHeader)
        assertEquals(HeaderTapTarget.SystemCalendar(atEpochMillis = nowMillis), actualHeader.date?.tapTarget)
        assertEquals(R.string.open_calendar, actualHeader.date?.clickLabel)
    }

    @Test
    fun `defaults both Weekday Header lines to the alarm list`() {
        val actualHeader: HeaderModel = applyStyle(style = ClockStyle.WEEKDAY)

        assertTimeDefault(header = actualHeader)
        assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.date?.tapTarget)
        assertEquals(R.string.open_alarms, actualHeader.date?.clickLabel)
    }

    @Test
    fun `applies Nothing to both Header lines in every style`() {
        ClockStyle.entries.forEach { style ->
            val actualHeader: HeaderModel = applyStyle(
                style = style,
                settings = HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = HeaderTapSetting.Nothing)
            )

            assertBothLines(header = actualHeader, expectedTarget = HeaderTapTarget.None, expectedLabel = null)
        }
    }

    @Test
    fun `applies a chosen Launchable with its click labels in every style`() {
        ClockStyle.entries.forEach { style ->
            val actualHeader: HeaderModel = applyStyle(
                style = style,
                settings = HeaderSettings(
                    timeTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key),
                    dateTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key)
                ),
                launchableState = createLoadedLaunchableState(listOf(inputLaunchable))
            )

            assertBothLines(
                header = actualHeader,
                expectedTarget = HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable),
                expectedLabel = R.string.open_launchable,
                expectedLabelArg = "Maps"
            )
        }
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
            launchableState = createLaunchableState(groups = emptyList(), readiness = LaunchableReadiness.Loading)
        )
        assertEquals(inputSetting, actualSetting)
    }

    @Test
    fun `opens the agenda from the time and the date in every style`() {
        ClockStyle.entries.forEach { style ->
            val actualHeader: HeaderModel = applyStyle(
                style = style,
                settings = HeaderSettings(
                    timeTapTarget = HeaderTapSetting.OpenAgenda,
                    dateTapTarget = HeaderTapSetting.OpenAgenda
                )
            )

            assertBothLines(header = actualHeader, expectedTarget = HeaderTapTarget.OpenAgenda, expectedLabel = R.string.open_agenda)
        }
    }

    @Test
    fun `keeps Open agenda while resolving the settings for display`() {
        val actualSetting: HeaderTapSetting = resolver.resolveSetting(
            setting = HeaderTapSetting.OpenAgenda,
            launchableState = createLoadedLaunchableState(listOf(inputLaunchable))
        )

        assertEquals(HeaderTapSetting.OpenAgenda, actualSetting)
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

    private fun applyStyle(
        style: ClockStyle,
        settings: HeaderSettings = HeaderSettings(),
        launchableState: LaunchableState = createLaunchableState(groups = emptyList())
    ): HeaderModel = resolver.applyTo(
        header = header(),
        settings = settings.copy(clockStyle = style),
        launchableState = launchableState,
        atEpochMillis = nowMillis
    )

    private fun assertTimeDefault(header: HeaderModel) {
        assertEquals(HeaderTapTarget.SystemAlarms, header.time?.tapTarget)
        assertEquals(R.string.open_alarms, header.time?.clickLabel)
    }

    private fun assertBothLines(
        header: HeaderModel,
        expectedTarget: HeaderTapTarget,
        expectedLabel: Int?,
        expectedLabelArg: String? = null
    ) {
        assertEquals(expectedTarget, header.time?.tapTarget)
        assertEquals(expectedLabel, header.time?.clickLabel)
        assertEquals(expectedLabelArg, header.time?.clickLabelArg)
        assertEquals(expectedTarget, header.date?.tapTarget)
        assertEquals(expectedLabel, header.date?.clickLabel)
        assertEquals(expectedLabelArg, header.date?.clickLabelArg)
    }

    private fun header(): HeaderModel = createHeaderModel(
        id = 1L,
        timeText = "2:05",
        spokenText = "2:05 PM",
        tapTarget = HeaderTapTarget.SystemAlarms,
        clickLabel = R.string.open_alarms,
        date = HeaderLineModel(
            text = "Tuesday, October 20",
            spokenText = "Tuesday, October 20, 2026",
            tapTarget = HeaderTapTarget.SystemCalendar(atEpochMillis = 0L),
            clickLabel = R.string.open_calendar
        )
    )
}
