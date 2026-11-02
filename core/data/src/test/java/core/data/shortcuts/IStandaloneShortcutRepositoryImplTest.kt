package core.data.shortcuts

import android.os.UserHandle
import core.data.apps.model.App
import core.data.favorites.FavoriteRecord
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.popups.IPopupRepository
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.awaitPin
import core.data.testing.createApp
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createShortcut
import core.data.testing.createShortcutPinCoordinator
import core.data.testing.createShortcutSourceMock
import core.data.testing.createStandaloneShortcutRepository
import core.data.testing.createUserHandle
import core.data.testing.createWidgetAreaRepositoryMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP_PACKAGE_NAME: String = "com.example.app"
private val inputOwner: App = createApp()
private val inputProfile: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)

@OptIn(ExperimentalCoroutinesApi::class)
class IStandaloneShortcutRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputKey: ShortcutKey = ShortcutKey(
        packageName = APP_PACKAGE_NAME,
        id = FIRST_SHORTCUT_ID,
        profile = inputProfile
    )

    @Test
    fun `resolves stored Standalone shortcuts through the Shortcut source`() = databaseTest {
        val inputResolution: MutableStateFlow<ShortcutResolution> = MutableStateFlow(ShortcutResolution())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = inputResolution)
        val repositories: StandaloneShortcutRepositories = createRepositories(mockShortcutSource = mockShortcutSource)
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = APP_PACKAGE_NAME,
            profile = inputProfile,
            longLabel = "Example site"
        )
        database.getStandaloneShortcutDao().insert(StandaloneShortcutRecord(key = inputKey.format(), addedAt = 1L))
        finishPendingWork()

        assertEquals(emptyList<ShortcutLaunchable>(), repositories.standaloneShortcuts.shortcuts.first())

        inputResolution.value = ShortcutResolution(shortcuts = mapOf(inputKey to inputShortcut))

        assertEquals(listOf(inputShortcut), repositories.standaloneShortcuts.shortcuts.first())
    }

    @Test
    fun `accept success stores and favorites the Shortcut, then pins it`() = databaseTest {
        val inputPinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val repositories: StandaloneShortcutRepositories = createRepositories(
            mockShortcutSource = createShortcutSourceMock(pinCalls = inputPinCalls)
        )
        finishPendingWork()
        inputPinCalls.value = emptyList()

        val actualAdded: Boolean = repositories.standaloneShortcuts.add(inputKey) { true }
        finishPendingWork()

        assertTrue(actualAdded)
        assertEquals(listOf(inputKey.format()), database.getStandaloneShortcutDao().getAll().map { record -> record.key })
        assertEquals(listOf(FavoriteRecord(key = inputKey.format(), position = 0)), favoriteRecords())
        inputPinCalls.awaitPin(PinCall(packageName = APP_PACKAGE_NAME, profile = inputProfile, ids = setOf(FIRST_SHORTCUT_ID)))
    }

    @Test
    fun `accept failure stores nothing and pins no Shortcut`() = databaseTest {
        val inputPinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val repositories: StandaloneShortcutRepositories = createRepositories(
            mockShortcutSource = createShortcutSourceMock(pinCalls = inputPinCalls)
        )
        finishPendingWork()
        inputPinCalls.value = emptyList()

        val actualAdded: Boolean = repositories.standaloneShortcuts.add(inputKey) { false }
        finishPendingWork()

        assertFalse(actualAdded)
        assertTrue(database.getStandaloneShortcutDao().getAll().isEmpty())
        assertTrue(favoriteRecords().isEmpty())
        assertTrue(inputPinCalls.value.none { call -> call.ids.isNotEmpty() })
    }

    @Test
    fun `a thrown accept stores nothing and pins no Shortcut`() = databaseTest {
        val inputPinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val repositories: StandaloneShortcutRepositories = createRepositories(
            mockShortcutSource = createShortcutSourceMock(pinCalls = inputPinCalls)
        )
        finishPendingWork()
        inputPinCalls.value = emptyList()

        val actualFailure: Result<Boolean> = runCatching {
            repositories.standaloneShortcuts.add(inputKey) { error("Rejected") }
        }
        finishPendingWork()

        assertTrue(actualFailure.isFailure)
        assertTrue(database.getStandaloneShortcutDao().getAll().isEmpty())
        assertTrue(favoriteRecords().isEmpty())
        assertTrue(inputPinCalls.value.none { call -> call.ids.isNotEmpty() })
    }

    @Test
    fun `a duplicate add keeps one row and the Favorites order`() = databaseTest {
        seedFavorite(key = inputOwner.key, position = 0)
        val inputPinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val repositories: StandaloneShortcutRepositories = createRepositories(
            mockShortcutSource = createShortcutSourceMock(pinCalls = inputPinCalls)
        )
        finishPendingWork()
        var actualAcceptCalls: Int = 0

        assertTrue(
            repositories.standaloneShortcuts.add(inputKey) {
                actualAcceptCalls += 1
                true
            }
        )
        assertTrue(
            repositories.standaloneShortcuts.add(inputKey) {
                actualAcceptCalls += 1
                true
            }
        )
        finishPendingWork()

        assertEquals(1, actualAcceptCalls)
        assertEquals(listOf(inputKey.format()), database.getStandaloneShortcutDao().getAll().map { record -> record.key })
        assertEquals(
            listOf(inputOwner.key, inputKey.format()),
            favoriteRecords().sortedBy { record -> record.position }.map { record -> record.key }
        )
    }

    @Test
    fun `editing a Pop-up for the same package does not unpin a Standalone shortcut`() = databaseTest {
        val inputPinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val repositories: StandaloneShortcutRepositories = createRepositories(
            mockShortcutSource = createShortcutSourceMock(pinCalls = inputPinCalls)
        )
        finishPendingWork()
        assertTrue(repositories.standaloneShortcuts.add(inputKey) { true })
        finishPendingWork()
        inputPinCalls.value = emptyList()
        val inputPopupKey: ShortcutKey = ShortcutKey(
            packageName = APP_PACKAGE_NAME,
            id = SECOND_SHORTCUT_ID,
            profile = inputProfile
        )

        repositories.popups.setItems(ownerKey = inputOwner.key, add = listOf(inputPopupKey.format()), remove = emptySet())
        inputPinCalls.awaitPin(
            PinCall(
                packageName = APP_PACKAGE_NAME,
                profile = inputProfile,
                ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)
            )
        )
        inputPinCalls.value = emptyList()

        repositories.popups.setItems(ownerKey = inputOwner.key, add = emptyList(), remove = setOf(inputPopupKey.format()))

        val expectedPin: PinCall = PinCall(
            packageName = APP_PACKAGE_NAME,
            profile = inputProfile,
            ids = setOf(FIRST_SHORTCUT_ID)
        )
        inputPinCalls.awaitPin(expectedPin)
        assertEquals(listOf(expectedPin), inputPinCalls.value)
    }

    private fun TestScope.createRepositories(
        mockShortcutSource: IShortcutSource,
        launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputOwner)))
    ): StandaloneShortcutRepositories {
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(state = launchableState)
        val coordinator: IShortcutPinCoordinator = createShortcutPinCoordinator(
            launchableRepository = mockLaunchableRepository,
            scope = backgroundScope,
            shortcutSource = mockShortcutSource,
            pinSources = listOf(
                IPopupShortcutPinSourceImpl(popupDao = database.getPopupDao()),
                IStandaloneShortcutPinSourceImpl(standaloneShortcutDao = database.getStandaloneShortcutDao())
            )
        )
        return StandaloneShortcutRepositories(
            standaloneShortcuts = createStandaloneShortcutRepository(
                standaloneShortcutDao = database.getStandaloneShortcutDao(),
                favoriteDao = database.getFavoriteDao(),
                shortcutSource = mockShortcutSource,
                shortcutPinCoordinator = coordinator
            ),
            popups = createPopupRepository(
                popupDao = database.getPopupDao(),
                launchableRepository = mockLaunchableRepository,
                scope = backgroundScope,
                shortcutSource = mockShortcutSource,
                widgetAreaRepository = createWidgetAreaRepositoryMock(),
                appWidgetHost = createAppWidgetHostMock(),
                shortcutPinCoordinator = coordinator
            )
        )
    }
}

private data class StandaloneShortcutRepositories(val standaloneShortcuts: IStandaloneShortcutRepository, val popups: IPopupRepository)
