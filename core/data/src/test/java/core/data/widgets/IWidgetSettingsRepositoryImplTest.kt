package core.data.widgets

import android.content.Context
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.testing.FakeHeaderSettingsDao
import core.data.testing.FakeLaunchableRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetSettingsRepositoryImplTest {
    private val keptLaunchable: Launchable = testLaunchable(key = "kept")

    @Test
    fun `returns defaults when the settings row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(dao = FakeHeaderSettingsDao())

        assertEquals(HeaderSettings(), repository.headerSettings.first())
    }

    @Test
    fun `persists a transformed Header setting`() = runTest {
        val repository: IWidgetSettingsRepository = createRepository(dao = FakeHeaderSettingsDao())

        repository.updateHeaderSettings { settings -> settings.copy(clockVisible = false) }

        assertEquals(HeaderSettings(clockVisible = false), repository.headerSettings.first())
    }

    @Test
    fun `keeps a tap target whose Launchable exists`() = runTest {
        val setting: HeaderTapSetting = HeaderTapSetting.Launchable(key = keptLaunchable.key)
        val dao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = setting))
        val repository: IWidgetSettingsRepository = createRepository(dao = dao, isLoaded = true)

        assertEquals(setting, repository.headerSettings.first().timeTapTarget)
        assertEquals(setting, dao.getRow()?.timeTapTarget)
    }

    @Test
    fun `keeps a stale tap target until the Launchables load`() = runTest {
        val staleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val dao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = staleSetting))
        val repository: IWidgetSettingsRepository = createRepository(dao = dao, isLoaded = false)

        assertEquals(staleSetting, repository.headerSettings.first().timeTapTarget)
        assertEquals(staleSetting, dao.getRow()?.timeTapTarget)
    }

    @Test
    fun `clears a stale tap target once the Launchables load`() = runTest {
        val staleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val keptSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = keptLaunchable.key)
        val dao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(timeTapTarget = staleSetting, dateTapTarget = keptSetting)
        )
        val launchables: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val repository: IWidgetSettingsRepository = createRepository(dao = dao, launchables = launchables)

        assertEquals(staleSetting, repository.headerSettings.first().timeTapTarget)

        launchables.setGroups(groups = listOf(LaunchableGroup.Builtin(items = listOf(keptLaunchable))))

        val settings: HeaderSettings = repository.headerSettings.first()
        assertNull(settings.timeTapTarget)
        assertEquals(keptSetting, settings.dateTapTarget)
        assertNull(dao.getRow()?.timeTapTarget)
        assertEquals(keptSetting, dao.getRow()?.dateTapTarget)
    }

    @Test
    fun `does not clear Nothing or the system default`() = runTest {
        val dao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing, dateTapTarget = null)
        )
        val repository: IWidgetSettingsRepository = createRepository(dao = dao, isLoaded = true)

        val settings: HeaderSettings = repository.headerSettings.first()
        assertEquals(HeaderTapSetting.Nothing, settings.timeTapTarget)
        assertNull(settings.dateTapTarget)
    }

    @Test
    fun `cleans up a stale target once for multiple collectors`() = runTest {
        val staleSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "uninstalled")
        val dao: FakeHeaderSettingsDao = FakeHeaderSettingsDao(HeaderSettings(timeTapTarget = staleSetting))
        val repository: IWidgetSettingsRepository = createRepository(dao = dao, isLoaded = true)
        val settings = List(2) { async { repository.headerSettings.first() } }

        advanceUntilIdle()
        settings.awaitAll()

        assertEquals(1, dao.upsertCount)
    }

    private fun createRepository(
        dao: FakeHeaderSettingsDao,
        isLoaded: Boolean = true,
        launchables: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.Builtin(items = listOf(keptLaunchable))),
            isLoaded = isLoaded
        )
    ): IWidgetSettingsRepository = IWidgetSettingsRepositoryImpl(
        headerSettingsDao = dao,
        launchableRepository = launchables
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
