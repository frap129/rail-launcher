package core.data.launchables

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.favorites.FavoriteRecord
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.testing.FIRST_POSITION
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakePreferencesRepository
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_SDK: Int = 35
private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_RES_ID: Int = 42
private const val ORIGINAL_NAME: String = "Original"
private const val RENAMED_NAME: String = "Renamed"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ILaunchableRepositoryImplTest {
    private val componentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)

    @Test
    fun `shares one upstream collection between every subscriber`() = runTest {
        val fakeApp: App = createApp()
        val fakeAppRepository: FakeAppRepository = FakeAppRepository(inputApps = listOf(fakeApp))
        val fakeIconRepository: FakeIconRepository = FakeIconRepository(inputIcon = Icon.ActivityIcon(componentName))
        val repository: ILaunchableRepository = createRepository(
            fakeAppRepository = fakeAppRepository,
            fakePreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            fakeIconRepository = fakeIconRepository
        )
        val fakeFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = fakeApp.key, position = FIRST_POSITION))
        )
        val favoritesRepository: IFavoritesRepositoryImpl = IFavoritesRepositoryImpl(
            favoriteDao = fakeFavoriteDao,
            launchableRepository = repository
        )
        val stateDeferred: Deferred<LaunchableState> = async { repository.launchableState.first { it.isLoaded } }
        // The item menu reads the same launchables flow.
        val menuItemsDeferred: Deferred<List<Launchable>> = async { repository.launchables.first { it.isNotEmpty() } }
        val groupsDeferred: Deferred<List<LaunchableGroup>> = async { repository.launchableGroups.first { it.isNotEmpty() } }
        val favoritesDeferred: Deferred<List<Launchable>> = async { favoritesRepository.favorites.first { it.isNotEmpty() } }
        val state: LaunchableState = stateDeferred.await()
        val menuItems: List<Launchable> = menuItemsDeferred.await()
        val groups: List<LaunchableGroup> = groupsDeferred.await()
        val favorites: List<Launchable> = favoritesDeferred.await()

        assertEquals(1, fakeAppRepository.collections)
        assertEquals(1, fakeIconRepository.iconRequests)
        assertTrue(state.isLoaded)
        assertTrue(menuItems.isNotEmpty())
        assertTrue(groups.isNotEmpty())
        assertTrue(favorites.isNotEmpty())
    }

    @Test
    fun `resolves Launchable State on the default dispatcher`() = runTest {
        val fakeAppRepository: FakeAppRepository = FakeAppRepository(inputApps = emptyList())
        val repository: ILaunchableRepository = createRepository(fakeAppRepository = fakeAppRepository)

        repository.launchableState.first { it.isLoaded }

        val actualThreadName: String = fakeAppRepository.collectionThreadNames.single()
        assertTrue(actualThreadName.startsWith("DefaultDispatcher-worker"))
    }

    @Test
    fun `resolves a renamed App into a new instance and leaves the original unchanged`() = runTest {
        val inputApp: App = createApp()
        val fakePreferencesRepository: FakePreferencesRepository = FakePreferencesRepository(
            inputCustomizations = listOf(LaunchableCustomization(key = inputApp.key, name = RENAMED_NAME))
        )
        val repository: ILaunchableRepository = createRepository(
            fakeAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            fakePreferencesRepository = fakePreferencesRepository
        )

        val actualApp: App = repository.launchableState.first { it.isLoaded }.launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(RENAMED_NAME, actualApp.name)
        assertEquals(ORIGINAL_NAME, inputApp.name)
        assertEquals(Icon.ActivityIcon(componentName), inputApp.icon)
    }

    @Test
    fun `resolves an icon pack into a new App instance and leaves the original unchanged`() = runTest {
        val inputApp: App = createApp()
        val expectedIcon: Icon = Icon.PackIcon(
            componentName = componentName,
            packPackageName = ICON_PACK_PACKAGE_NAME,
            resId = ICON_PACK_RES_ID
        )
        val repository: ILaunchableRepository = createRepository(
            fakeAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            fakePreferencesRepository = FakePreferencesRepository(
                inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)
            ),
            fakeIconRepository = FakeIconRepository(inputIcon = expectedIcon)
        )

        val actualApp: App = repository.launchableState.first { it.isLoaded }.launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(expectedIcon, actualApp.icon)
        assertEquals(Icon.ActivityIcon(componentName), inputApp.icon)
    }

    private fun TestScope.createRepository(
        fakeAppRepository: IAppRepository,
        fakePreferencesRepository: IPreferencesRepository = FakePreferencesRepository(),
        fakeIconRepository: IIconRepository = FakeIconRepository(
            inputIcon = Icon.ActivityIcon(componentName)
        )
    ): ILaunchableRepository = ILaunchableRepositoryImpl(
        appRepository = fakeAppRepository,
        iconRepository = fakeIconRepository,
        preferencesRepository = fakePreferencesRepository,
        scope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Default)
    )

    private fun createApp(): App = App(
        defaultName = ORIGINAL_NAME,
        profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
        componentName = componentName
    )
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class FakeAppRepository(inputApps: List<App>) : IAppRepository {
    private val state: MutableStateFlow<List<App>?> = MutableStateFlow(inputApps)
    private val collectionCounter: AtomicInteger = AtomicInteger(0)
    private val recordedThreadNames: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override val apps: StateFlow<List<App>?> = object : StateFlow<List<App>?> by state {
        override suspend fun collect(collector: FlowCollector<List<App>?>): Nothing {
            collectionCounter.incrementAndGet()
            recordedThreadNames.add(Thread.currentThread().name)
            return state.collect(collector)
        }
    }

    val collections: Int get() = collectionCounter.get()
    val collectionThreadNames: List<String> get() = recordedThreadNames.toList()
}

private class FakeIconRepository(private val inputIcon: Icon) : IIconRepository {
    private val iconRequestCounter: AtomicInteger = AtomicInteger(0)
    override val iconPacks: StateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack))
    val iconRequests: Int get() = iconRequestCounter.get()

    override suspend fun getIcon(packPackageName: String, componentName: ComponentName): Icon {
        iconRequestCounter.incrementAndGet()
        return inputIcon
    }
}
