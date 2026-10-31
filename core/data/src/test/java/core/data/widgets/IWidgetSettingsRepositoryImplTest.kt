package core.data.widgets

import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.profiles.ProfileKind
import core.data.profiles.toProfileToken
import core.data.room.dao.HeaderSettingsDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createProfileInfo
import core.data.testing.createRailLaunchable
import core.data.testing.packageLevelKey
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetSettingsRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputKeptLaunchable: Launchable = createRailLaunchable(name = "kept")

    @Test
    fun `returns defaults when the settings row is absent`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(HeaderSettings(), repository.headerSettings.first())
    }

    @Test
    fun `persists a transformed Header setting`() = databaseTest {
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
    fun `returns Event settings defaults when the row is absent`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(EventSettings(), repository.eventSettings.first())
    }

    @Test
    fun `round trips excluded calendar ids through Event settings`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()
        val inputExcluded: Set<String> = setOf("42", "w:7")

        repository.setExcludedCalendarIds(inputExcluded)

        assertEquals(inputExcluded, repository.eventSettings.first().excludedCalendarIds)
    }

    @Test
    fun `clamps a stored event count on read`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository(eventSettings = EventSettings(maxEvents = 9))

        assertEquals(3, repository.eventSettings.first().maxEvents)
    }

    @Test
    fun `persists Event settings through their setters`() = databaseTest {
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
    fun `persists Widget Area height limit settings`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        repository.setHeightLimitEnabled(false)
        repository.setHeightLimit(WidgetAreaHeightLimit.THREE_QUARTERS)

        val actualSettings: WidgetAreaSettings = repository.widgetAreaSettings.first()
        assertFalse(actualSettings.heightLimitEnabled)
        assertEquals(WidgetAreaHeightLimit.THREE_QUARTERS, actualSettings.heightLimit)
        assertEquals(actualSettings, requireNotNull(database.getWidgetAreaSettingsDao().getRow()).toDomain())
    }

    @Test
    fun `returns Media Player settings defaults when the row is absent`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        assertEquals(MediaPlayerSettings.Default, repository.mediaPlayerSettings.first())
    }

    @Test
    fun `persists the Media Player enabled switch`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        repository.setMediaPlayerEnabled(true)

        assertTrue(repository.mediaPlayerSettings.first().enabled)
        assertTrue(requireNotNull(database.getMediaPlayerSettingsDao().getRow()).enabled)
    }

    @Test
    fun `exposes the Media Player option defaults`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()
        val actualSettings: MediaPlayerSettings = repository.mediaPlayerSettings.first()
        assertTrue(actualSettings.showWhenPaused)
        assertTrue(actualSettings.showArtwork)
        assertEquals(MediaPlayerPlacement.BELOW_WIDGET_AREA, actualSettings.placement)
        assertTrue(actualSettings.excludedPackages.isEmpty())
    }

    @Test
    fun `persists the Media Player display options`() = databaseTest {
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
    fun `adds and removes excluded Media Player packages`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.music", excluded = true)
        repository.setMediaPlayerPackageExcluded(packageName = "com.example.video", excluded = false)
        assertEquals(setOf("com.example.music"), repository.mediaPlayerSettings.first().excludedPackages)
    }

    @Test
    fun `setClockVisible rejects hiding the last standing part`() = databaseTest {
        val inputBatteries: List<BatteryVisibility> =
            listOf(BatteryVisibility.NEVER, BatteryVisibility.CHARGING_OR_LOW)
        inputBatteries.forEach { inputBattery ->
            val inputSettings: HeaderSettings = HeaderSettings(
                clockVisible = true,
                dateVisible = false,
                batteryVisibility = inputBattery,
                timeTapTarget = HeaderTapSetting.SystemDefault,
                dateTapTarget = HeaderTapSetting.SystemDefault
            )
            val repository: IWidgetSettingsRepository = createRepository(headerSettings = inputSettings)

            repository.setClockVisible(visible = false)

            assertEquals(inputSettings, database.getHeaderSettingsDao().getRow())
        }
    }

    @Test
    fun `setClockVisible hides the clock while the battery is always shown`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )

        repository.setClockVisible(visible = false)

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertFalse(actualSettings.clockVisible)
        assertFalse(requireNotNull(database.getHeaderSettingsDao().getRow()).clockVisible)
    }

    @Test
    fun `setDateVisible hides the date while the battery is always shown`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )

        repository.setDateVisible(visible = false)

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertFalse(actualSettings.dateVisible)
        assertFalse(requireNotNull(database.getHeaderSettingsDao().getRow()).dateVisible)
    }

    @Test
    fun `setBatteryVisibility persists the chosen visibility`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository()

        repository.setBatteryVisibility(visibility = BatteryVisibility.ALWAYS)

        assertEquals(BatteryVisibility.ALWAYS, repository.headerSettings.first().batteryVisibility)
    }

    @Test
    fun `pruneExcludedCalendarIds keeps only the available calendars`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository(
            eventSettings = EventSettings(excludedCalendarIds = setOf("1", "2"))
        )

        repository.pruneExcludedCalendarIds(availableCalendarIds = setOf("1"))

        assertEquals(setOf("1"), repository.eventSettings.first().excludedCalendarIds)
    }

    @Test
    fun `pruneExcludedCalendarIds keeps the exclusions with no available calendars`() = databaseTest {
        val inputExcluded: Set<String> = setOf("1", "2")
        val repository: IWidgetSettingsRepository = createRepository(
            eventSettings = EventSettings(excludedCalendarIds = inputExcluded)
        )

        repository.pruneExcludedCalendarIds(availableCalendarIds = emptySet())

        assertEquals(inputExcluded, repository.eventSettings.first().excludedCalendarIds)
    }

    @Test
    fun `setBatteryVisibility never re-enables a hidden clock`() = databaseTest {
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
    fun `setBatteryVisibility rejects hiding the last visible part`() = databaseTest {
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
    fun `keeps a tap target whose Launchable exists`() = databaseTest {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKeptLaunchable.key)
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputSetting),
            isLoaded = true
        )

        assertEquals(inputSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputSetting, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    @Test
    fun `clears a tap target stored under a package-level key while its App stays`() = databaseTest {
        val inputApp: App = createApp(packageName = "com.example.app")
        val inputCurrentSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputApp.key)
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = inputApp.packageLevelKey()),
                dateTapTarget = inputCurrentSetting
            ),
            mockLaunchables = createLaunchableRepositoryMock(launchables = listOf(inputApp))
        )

        val actualSettings: HeaderSettings = repository.headerSettings.first()

        assertNull(actualSettings.timeTapTarget)
        assertEquals(inputCurrentSetting, actualSettings.dateTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
        assertEquals(inputCurrentSetting, database.getHeaderSettingsDao().getRow()?.dateTapTarget)
    }

    @Test
    fun `keeps a stale tap target until the Launchables load`() = databaseTest {
        val inputStaleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputStaleSetting),
            isLoaded = false
        )

        assertEquals(inputStaleSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputStaleSetting, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    @Test
    fun `clears a stale tap target once the Launchables load`() = databaseTest {
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

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertNull(actualSettings.timeTapTarget)
        assertEquals(inputKeptSetting, actualSettings.dateTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
        assertEquals(inputKeptSetting, database.getHeaderSettingsDao().getRow()?.dateTapTarget)
    }

    @Test
    fun `keeps a Locked Profile's tap target until the Profile is removed`() = databaseTest {
        val inputPrivateProfile = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp = createApp(name = "Secret", packageName = "com.example.secret", profileInfo = inputPrivateProfile)
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputPrivateApp.key)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), keptKeys = setOf(inputPrivateProfile.user.toProfileToken()))
        )
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = inputSetting),
            mockLaunchables = createLaunchableRepositoryMock(launchableState)
        )

        assertEquals(inputSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(inputSetting, database.getHeaderSettingsDao().getRow()?.timeTapTarget)

        launchableState.value = createLaunchableState(groups = emptyList())

        assertNull(repository.headerSettings.first().timeTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    @Test
    fun `does not clear Nothing or the system default`() = databaseTest {
        val repository: IWidgetSettingsRepository = createRepository(
            headerSettings = HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = null),
            isLoaded = true
        )

        val actualSettings: HeaderSettings = repository.headerSettings.first()
        assertEquals(HeaderTapSetting.Nothing, actualSettings.timeTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, actualSettings.dateTapTarget)
    }

    @Test
    fun `cleans up a stale target once for multiple collectors`() = databaseTest {
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
