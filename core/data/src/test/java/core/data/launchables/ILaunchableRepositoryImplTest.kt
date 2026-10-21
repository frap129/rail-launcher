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
import core.data.testing.TEST_SDK
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_RES_ID: Int = 42
private const val ORIGINAL_NAME: String = "Original"
private const val RENAMED_NAME: String = "Renamed"
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L

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
            launchableRepository = repository,
            scope = backgroundScope
        )
        val stateDeferred: Deferred<LaunchableState> = async { repository.launchableState.first { it.isLoaded } }
        // The item menu reads the same launchables flow.
        val menuItemsDeferred: Deferred<List<Launchable>> = async { repository.launchables.first { it.isNotEmpty() } }
        val favoritesDeferred: Deferred<List<Launchable>> = async { favoritesRepository.favorites.first { it.isNotEmpty() } }
        val state: LaunchableState = stateDeferred.await()
        val menuItems: List<Launchable> = menuItemsDeferred.await()
        val favorites: List<Launchable> = favoritesDeferred.await()

        assertEquals(1, fakeAppRepository.collections)
        assertEquals(1, fakeIconRepository.iconRequests)
        assertTrue(state.isLoaded)
        assertEquals(listOf("O", "⌂"), state.listedGroups.map { group -> group.label })
        assertTrue(menuItems.isNotEmpty())
        assertTrue(favorites.isNotEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = runTest {
        val inputApp: App = createApp()
        val inputRenamedApp: App = createApp(name = RENAMED_NAME)
        val fakeAppRepository: FakeAppRepository = FakeAppRepository(inputApps = listOf(inputApp))
        val repository: ILaunchableRepository = createRepository(
            fakeAppRepository = fakeAppRepository,
            scope = backgroundScope
        )
        backgroundScope.launch { repository.launchableState.collect() }
        runCurrent()
        assertTrue(repository.launchableState.value.isLoaded)
        fakeAppRepository.failNextCollections(count = 2)
        fakeAppRepository.setApps(listOf(inputRenamedApp))
        runCurrent()
        assertEquals(1, fakeAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(1, fakeAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fakeAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(2 * INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(2, fakeAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, fakeAppRepository.collections)
        assertEquals(RENAMED_NAME, repository.launchableState.value.singleAppName())
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
    fun `keeps the Star and Builtin groups when there are no Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(fakeAppRepository = FakeAppRepository(inputApps = emptyList()))

        val actual: List<String> = repository.launchableState.first { it.isLoaded }.groups.map { group -> group.label }

        assertEquals(listOf("★", "⌂"), actual)
    }

    @Test
    fun `resolves Builtin Launchables alongside Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(fakeAppRepository = FakeAppRepository(inputApps = emptyList()))
        val state: LaunchableState = repository.launchableState.first { it.isLoaded }
        val builtinItems: List<Launchable> = state.groups
            .filterIsInstance<LaunchableGroup.Builtin>()
            .flatMap { group -> group.items }

        assertTrue(builtinItems.isNotEmpty())
        assertTrue(state.launchables.containsAll(builtinItems))
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
        ),
        scope: CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Default)
    ): ILaunchableRepository = ILaunchableRepositoryImpl(
        appRepository = fakeAppRepository,
        iconRepository = fakeIconRepository,
        preferencesRepository = fakePreferencesRepository,
        scope = scope
    )

    private fun createApp(name: String = ORIGINAL_NAME): App = App(
        defaultName = name,
        profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
        componentName = componentName
    )

    private fun LaunchableState.singleAppName(): String = launchables.filterIsInstance<App>().single().name
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class FakeAppRepository(inputApps: List<App>) : IAppRepository {
    private val state: MutableStateFlow<List<App>?> = MutableStateFlow(inputApps)
    private val collectionCounter: AtomicInteger = AtomicInteger(0)
    private val remainingFailures: AtomicInteger = AtomicInteger(0)
    private val recordedThreadNames: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override val apps: StateFlow<List<App>?> = object : StateFlow<List<App>?> by state {
        override suspend fun collect(collector: FlowCollector<List<App>?>): Nothing {
            collectionCounter.incrementAndGet()
            recordedThreadNames.add(Thread.currentThread().name)
            return state.collect { apps ->
                if (remainingFailures.getAndUpdate { count -> if (count > 0) count - 1 else 0 } > 0) {
                    throw IllegalStateException("Apps upstream failure")
                }
                collector.emit(apps)
            }
        }
    }

    val collections: Int get() = collectionCounter.get()
    val collectionThreadNames: List<String> get() = recordedThreadNames.toList()

    fun failNextCollections(count: Int) {
        remainingFailures.set(count)
    }

    fun setApps(apps: List<App>) {
        state.value = apps
    }
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
