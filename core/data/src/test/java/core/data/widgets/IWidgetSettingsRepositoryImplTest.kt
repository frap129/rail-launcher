package core.data.widgets

import android.content.Context
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.testing.FakeEventSettingsDao
import core.data.testing.FakeHeaderSettingsDao
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakeMediaPlayerSettingsDao
import core.data.testing.FakeWidgetAreaSettingsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetSettingsRepositoryImplTest {
    private val inputKeptLaunchable: Launchable = testLaunchable(key = "kept")

    @Test
    fun `returns defaults when the settings row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())

        assertEquals(HeaderSettings(), repository.headerSettings.first())
    }

    @Test
    fun `persists a transformed Header setting`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())

        repository.updateHeaderSettings { settings -> settings.copy(clockVisible = false) }

        assertEquals(HeaderSettings(clockVisible = false), repository.headerSettings.first())
    }

    @Test
    fun `returns Event settings defaults when the row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())

        assertEquals(EventSettings(), repository.eventSettings.first())
    }

    @Test
    fun `round trips excluded calendar ids through Event settings`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())
        val inputExcluded: Set<String> = setOf("42", "w:7")

        repository.setExcludedCalendarIds(inputExcluded)

        assertEquals(inputExcluded, repository.eventSettings.first().excludedCalendarIds)
    }

    @Test
    fun `clamps a stored event count on read`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(
            mockHeaderSettingsDao = FakeHeaderSettingsDao(),
            mockEventSettingsDao = FakeEventSettingsDao(EventSettings(maxEvents = 9))
        )

        assertEquals(3, repository.eventSettings.first().maxEvents)
    }

    @Test
    fun `persists Event settings through their setters`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())

        repository.setEventsEnabled(true)
        repository.setEventLookaheadHours(24)
        repository.setEventMaxEvents(2)
        repository.setEventIncludeAllDay(false)

        val actualSettings: EventSettings = repository.eventSettings.first()
        assertTrue(actualSettings.enabled)
        assertEquals(24, actualSettings.lookaheadHours)
        assertEquals(2, actualSettings.maxEvents)
        assertFalse(actualSettings.includeAllDay)
    }

    @Test
    fun `persists Widget Area height limit settings`() = runTest {
        val mockAreaSettingsDao: FakeWidgetAreaSettingsDao = FakeWidgetAreaSettingsDao()
        val repository: IWidgetSettingsRepository = createRepository(
            mockHeaderSettingsDao = FakeHeaderSettingsDao(),
            mockAreaSettingsDao = mockAreaSettingsDao
        )

        repository.setHeightLimitEnabled(false)
        repository.setHeightLimit(WidgetAreaHeightLimit.THREE_QUARTERS)

        val actualSettings: WidgetAreaSettings = repository.widgetAreaSettings.first()
        assertFalse(actualSettings.heightLimitEnabled)
        assertEquals(WidgetAreaHeightLimit.THREE_QUARTERS, actualSettings.heightLimit)
        assertEquals(actualSettings, requireNotNull(mockAreaSettingsDao.getRow()).toDomain())
    }

    @Test
    fun `returns Media Player settings defaults when the row is absent`() = runTest {
        val repository: IWidgetSettingsRepository =
            createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao(), mockMediaPlayerSettingsDao = FakeMediaPlayerSettingsDao())

        assertEquals(MediaPlayerSettings.Default, repository.mediaPlayerSettings.first())
    }

    @Test
    fun `persists the Media Player enabled switch`() = runTest {
        val mockMediaPlayerSettingsDao: FakeMediaPlayerSettingsDao = FakeMediaPlayerSettingsDao()
        val repository: IWidgetSettingsRepository =
            createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao(), mockMediaPlayerSettingsDao = mockMediaPlayerSettingsDao)

        repository.setMediaPlayerEnabled(true)

        assertTrue(repository.mediaPlayerSettings.first().enabled)
        assertTrue(requireNotNull(mockMediaPlayerSettingsDao.getRow()).enabled)
    }

    @Test
    fun `exposes the Media Player option defaults`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao())
        val actualSettings: MediaPlayerSettings = repository.mediaPlayerSettings.first()
        assertTrue(actualSettings.showWhenPaused)
        assertTrue(actualSettings.showArtwork)
        assertEquals(MediaPlayerPlacement.BELOW_WIDGET_AREA, actualSettings.placement)
        assertTrue(actualSettings.excludedPackages.isEmpty())
    }

    @Test
    fun `persists the Media Player display options`() = runTest {
        val mockMediaPlayerSettingsDao: FakeMediaPlayerSettingsDao = FakeMediaPlayerSettingsDao()
        val repository: IWidgetSettingsRepository =
            createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao(), mockMediaPlayerSettingsDao = mockMediaPlayerSettingsDao)
        repository.setMediaPlayerShowWhenPaused(false)
        repository.setMediaPlayerShowArtwork(false)
        repository.setMediaPlayerPlacement(MediaPlayerPlacement.TOP_OF_WIDGET_AREA)
        val actualSettings: MediaPlayerSettings = repository.mediaPlayerSettings.first()
        assertFalse(actualSettings.showWhenPaused)
        assertFalse(actualSettings.showArtwork)
        assertEquals(MediaPlayerPlacement.TOP_OF_WIDGET_AREA, actualSettings.placement)
        assertEquals(actualSettings, requireNotNull(mockMediaPlayerSettingsDao.getRow()))
    }

    @Test
    fun `adds and removes excluded Media Player packages`() = runTest {
        val mockMediaPlayerSettingsDao: FakeMediaPlayerSettingsDao = FakeMediaPlayerSettingsDao()
        val repository: IWidgetSettingsRepository =
            createRepository(mockHeaderSettingsDao = FakeHeaderSettingsDao(), mockMediaPlayerSettingsDao = mockMediaPlayerSettingsDao)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.music", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = false)
        assertEquals(setOf("com.example.music"), repository.mediaPlayerSettings.first().excludedPackages)
    }

    @Test
    fun `setClockVisible rejects hiding the last standing part`() = runTest {
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(clockVisible = true, dateVisible = false)
        )
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)

        repository.setClockVisible(visible = false)

        assertEquals(0, mockHeaderSettingsDao.upsertCount)
    }

    @Test
    fun `setBatteryVisibility never re-enables a hidden clock`() = runTest {
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(clockVisible = false, dateVisible = true, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)

        repository.setBatteryVisibility(BatteryVisibility.NEVER)

        val actualSettings: HeaderSettings = requireNotNull(mockHeaderSettingsDao.getRow())
        assertEquals(BatteryVisibility.NEVER, actualSettings.batteryVisibility)
        assertFalse(actualSettings.clockVisible)
    }

    @Test
    fun `setBatteryVisibility rejects hiding the last visible part`() = runTest {
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(clockVisible = false, dateVisible = false, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)

        repository.setBatteryVisibility(BatteryVisibility.NEVER)

        assertEquals(0, mockHeaderSettingsDao.upsertCount)
    }

    @Test
    fun `keeps a tap target whose Launchable exists`() = runTest {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKeptLaunchable.key)
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = inputSetting))
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)

        assertEquals(inputSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputSetting, mockHeaderSettingsDao.getRow()?.timeTapTarget)
    }

    @Test
    fun `keeps a stale tap target until the Launchables load`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = inputStaleSetting))
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = false)

        assertEquals(inputStaleSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputStaleSetting, mockHeaderSettingsDao.getRow()?.timeTapTarget)
    }

    @Test
    fun `clears a stale tap target once the Launchables load`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val inputKeptSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKeptLaunchable.key)
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(timeTapTarget = inputStaleSetting, dateTapTarget = inputKeptSetting)
        )
        val mockLaunchables: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val repository: IWidgetSettingsRepository =
            createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, mockLaunchables = mockLaunchables)

        assertEquals(inputStaleSetting, repository.headerSettings.first().timeTapTarget)

        mockLaunchables.setGroups(groups = listOf(LaunchableGroup.Builtin(items = listOf(inputKeptLaunchable))))

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertNull(actualSettings.timeTapTarget)
        assertEquals(inputKeptSetting, actualSettings.dateTapTarget)
        assertNull(mockHeaderSettingsDao.getRow()?.timeTapTarget)
        assertEquals(inputKeptSetting, mockHeaderSettingsDao.getRow()?.dateTapTarget)
    }

    @Test
    fun `does not clear Nothing or the system default`() = runTest {
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = null)
        )
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertEquals(HeaderTapSetting.Nothing, actualSettings.timeTapTarget)
        assertNull(actualSettings.dateTapTarget)
    }

    @Test
    fun `cleans up a stale target once for multiple collectors`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val mockHeaderSettingsDao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = inputStaleSetting))
        val repository: IWidgetSettingsRepository = createRepository(mockHeaderSettingsDao = mockHeaderSettingsDao, isLoaded = true)
        val actualSettings = List(2) { async { repository.headerSettings.first() } }

        advanceUntilIdle()
        actualSettings.awaitAll()

        assertEquals(1, mockHeaderSettingsDao.upsertCount)
    }

    private fun createRepository(
        mockHeaderSettingsDao: FakeHeaderSettingsDao,
        mockEventSettingsDao: FakeEventSettingsDao = FakeEventSettingsDao(),
        mockAreaSettingsDao: FakeWidgetAreaSettingsDao = FakeWidgetAreaSettingsDao(),
        mockMediaPlayerSettingsDao: FakeMediaPlayerSettingsDao = FakeMediaPlayerSettingsDao(),
        isLoaded: Boolean = true,
        mockLaunchables: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.Builtin(items = listOf(inputKeptLaunchable))),
            isLoaded = isLoaded
        )
    ): IWidgetSettingsRepository = IWidgetSettingsRepositoryImpl(
        headerSettingsDao = mockHeaderSettingsDao,
        eventSettingsDao = mockEventSettingsDao,
        widgetAreaSettingsDao = mockAreaSettingsDao,
        mediaPlayerSettingsDao = mockMediaPlayerSettingsDao,
        launchableRepository = mockLaunchables
    )

    private fun testLaunchable(key: String): Launchable = object : Launchable() {
        override val key: String = key
        override val defaultName: String = key
        override val defaultIcon: Icon = Icon.ApplicationIcon(packageName = key)
        override val name: String = key
        override val icon: Icon = defaultIcon

        override fun withCustomization(appearance: LaunchableAppearance): Launchable = this

        override fun launch(context: Context) = Unit
    }
}
