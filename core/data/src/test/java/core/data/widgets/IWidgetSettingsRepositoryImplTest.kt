package core.data.widgets

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.room.dao.HeaderSettingsDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createRailLaunchable
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetSettingsRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputKeptLaunchable: Launchable = createRailLaunchable(name = "kept")

    @Test
    fun `returns defaults when the settings row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(HeaderSettings(), repository.headerSettings.first())
    }

    @Test
    fun `persists a transformed Header setting`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

        repository.updateHeaderSettings { settings -> settings.copy(clockVisible = false) }

        assertEquals(
            HeaderSettings(
                clockVisible = false,
                timeTapTarget = HeaderTapSetting.SystemDefault,
                dateTapTarget = HeaderTapSetting.SystemDefault
            ),
            repository.headerSettings.first()
        )
    }

    @Test
    fun `returns Event settings defaults when the row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(EventSettings(), repository.eventSettings.first())
    }

    @Test
    fun `round trips excluded calendar ids through Event settings`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()
        val inputExcluded: Set<String> = setOf("42", "w:7")

        repository.setExcludedCalendarIds(inputExcluded)

        assertEquals(inputExcluded, repository.eventSettings.first().excludedCalendarIds)
    }

    @Test
    fun `clamps a stored event count on read`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(eventSettings = EventSettings(maxEvents = 9))

        assertEquals(3, repository.eventSettings.first().maxEvents)
    }

    @Test
    fun `persists Event settings through their setters`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

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
        val repository: IWidgetSettingsRepository = createRepository()

        repository.setHeightLimitEnabled(false)
        repository.setHeightLimit(WidgetAreaHeightLimit.THREE_QUARTERS)

        val actualSettings: WidgetAreaSettings = repository.widgetAreaSettings.first()
        assertFalse(actualSettings.heightLimitEnabled)
        assertEquals(WidgetAreaHeightLimit.THREE_QUARTERS, actualSettings.heightLimit)
        assertEquals(actualSettings, requireNotNull(database.getWidgetAreaSettingsDao().getRow()).toDomain())
    }

    @Test
    fun `returns Media Player settings defaults when the row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(MediaPlayerSettings.Default, repository.mediaPlayerSettings.first())
    }

    @Test
    fun `persists the Media Player enabled switch`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()

        repository.setMediaPlayerEnabled(true)

        assertTrue(repository.mediaPlayerSettings.first().enabled)
        assertTrue(requireNotNull(database.getMediaPlayerSettingsDao().getRow()).enabled)
    }

    @Test
    fun `exposes the Media Player option defaults`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()
        val actualSettings: MediaPlayerSettings = repository.mediaPlayerSettings.first()
        assertTrue(actualSettings.showWhenPaused)
        assertTrue(actualSettings.showArtwork)
        assertEquals(MediaPlayerPlacement.BELOW_WIDGET_AREA, actualSettings.placement)
        assertTrue(actualSettings.excludedPackages.isEmpty())
    }

    @Test
    fun `persists the Media Player display options`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()
        repository.setMediaPlayerShowWhenPaused(false)
        repository.setMediaPlayerShowArtwork(false)
        repository.setMediaPlayerPlacement(MediaPlayerPlacement.TOP_OF_WIDGET_AREA)
        val actualSettings: MediaPlayerSettings = repository.mediaPlayerSettings.first()
        assertFalse(actualSettings.showWhenPaused)
        assertFalse(actualSettings.showArtwork)
        assertEquals(MediaPlayerPlacement.TOP_OF_WIDGET_AREA, actualSettings.placement)
        assertEquals(actualSettings, requireNotNull(database.getMediaPlayerSettingsDao().getRow()))
    }

    @Test
    fun `adds and removes excluded Media Player packages`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository()
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.music", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = false)
        assertEquals(setOf("com.example.music"), repository.mediaPlayerSettings.first().excludedPackages)
    }

    @Test
    fun `setClockVisible rejects hiding the last standing part`() = runTest {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = true,
            dateVisible = false,
            timeTapTarget = HeaderTapSetting.SystemDefault,
            dateTapTarget = HeaderTapSetting.SystemDefault
        )
        val repository: IWidgetSettingsRepository = createRepository(headerSettings = inputSettings, isLoaded = true)

        repository.setClockVisible(visible = false)

        assertEquals(inputSettings, database.getHeaderSettingsDao().getRow())
    }

    @Test
    fun `setBatteryVisibility never re-enables a hidden clock`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(clockVisible = false, dateVisible = true, batteryVisibility = BatteryVisibility.ALWAYS),
            isLoaded = true
        )

        repository.setBatteryVisibility(BatteryVisibility.NEVER)

        val actualSettings: HeaderSettings = requireNotNull(database.getHeaderSettingsDao().getRow())
        assertEquals(BatteryVisibility.NEVER, actualSettings.batteryVisibility)
        assertFalse(actualSettings.clockVisible)
    }

    @Test
    fun `setBatteryVisibility rejects hiding the last visible part`() = runTest {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = false,
            timeTapTarget = HeaderTapSetting.SystemDefault,
            dateTapTarget = HeaderTapSetting.SystemDefault,
            batteryVisibility = BatteryVisibility.ALWAYS
        )
        val repository: IWidgetSettingsRepository = createRepository(headerSettings = inputSettings, isLoaded = true)

        repository.setBatteryVisibility(BatteryVisibility.NEVER)

        assertEquals(inputSettings, database.getHeaderSettingsDao().getRow())
    }

    @Test
    fun `keeps a tap target whose Launchable exists`() = runTest {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKeptLaunchable.key)
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputSetting),
            isLoaded = true
        )

        assertEquals(inputSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputSetting, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    @Test
    fun `keeps a stale tap target until the Launchables load`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputStaleSetting),
            isLoaded = false
        )

        assertEquals(inputStaleSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputStaleSetting, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    @Test
    fun `clears a stale tap target once the Launchables load`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val inputKeptSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKeptLaunchable.key)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchables: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputStaleSetting, dateTapTarget = inputKeptSetting),
            mockLaunchables = mockLaunchables
        )

        assertEquals(inputStaleSetting, repository.headerSettings.first().timeTapTarget)

        launchableState.value = createLaunchableState(
            groups = listOf(LaunchableGroup.Builtin(items = listOf(inputKeptLaunchable)))
        )

        val actualSettings: HeaderSettings = repository.headerSettings.first { settings -> settings.timeTapTarget == null }
        assertNull(actualSettings.timeTapTarget)
        assertEquals(inputKeptSetting, actualSettings.dateTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
        assertEquals(inputKeptSetting, database.getHeaderSettingsDao().getRow()?.dateTapTarget)
    }

    @Test
    fun `does not clear Nothing or the system default`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = null),
            isLoaded = true
        )

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertEquals(HeaderTapSetting.Nothing, actualSettings.timeTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, actualSettings.dateTapTarget)
    }

    @Test
    fun `cleans up a stale target once for multiple collectors`() = runTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        database.getHeaderSettingsDao().upsert(HeaderSettings(timeTapTarget = inputStaleSetting))
        val mockHeaderSettingsDao: HeaderSettingsDao = spyk(database.getHeaderSettingsDao())
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettingsDao = mockHeaderSettingsDao,
            mockLaunchables = createLaunchableRepositoryMock(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputKeptLaunchable)))
            )
        )
        val actualSettings = List(2) { async { repository.headerSettings.first() } }

        advanceUntilIdle()
        actualSettings.awaitAll()

        coVerify(exactly = 1) { mockHeaderSettingsDao.upsert(any()) }
    }

    private suspend fun createRepository(
        headerSettings: HeaderSettings? = null,
        eventSettings: EventSettings? = null,
        headerSettingsDao: HeaderSettingsDao = database.getHeaderSettingsDao(),
        isLoaded: Boolean = true,
        mockLaunchables: ILaunchableRepository = createLaunchableRepositoryMock(
            groups = listOf(LaunchableGroup.Builtin(items = listOf(inputKeptLaunchable))),
            isLoaded = isLoaded
        )
    ): IWidgetSettingsRepository {
        headerSettings?.let { settings -> headerSettingsDao.upsert(settings) }
        eventSettings?.let { settings -> database.getEventSettingsDao().upsert(settings) }
        return IWidgetSettingsRepositoryImpl(
            headerSettingsDao = headerSettingsDao,
            eventSettingsDao = database.getEventSettingsDao(),
            widgetAreaSettingsDao = database.getWidgetAreaSettingsDao(),
            mediaPlayerSettingsDao = database.getMediaPlayerSettingsDao(),
            launchableRepository = mockLaunchables
        )
    }
}
