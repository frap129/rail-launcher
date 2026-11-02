package core.data.shortcuts

import android.os.UserHandle
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.LaunchableState
import core.data.popups.PopupItemRecord
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.room.TransactionEffects
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PRIVATE_PACKAGE_NAME
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.awaitPin
import core.data.testing.createApp
import core.data.testing.createAppProfile
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createFixedPrivateSpaceStatus
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createPrivateApp
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createProfileInfo
import core.data.testing.createShortcutPinCoordinator
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val GONE_PACKAGE_NAME: String = "com.example.gone"
private const val TEST_USER_ID: Int = 0
private val inputOwner: App = createApp()
private val inputOtherOwner: App = createApp(name = "Other", packageName = "com.example.other")

@OptIn(ExperimentalCoroutinesApi::class)
class IShortcutPinCoordinatorImplTest : InMemoryDatabaseTest() {
    private val profile: UserHandle = UserHandle.getUserHandleForUid(TEST_USER_ID)
    private val appProfile: AppProfile = createAppProfile(packageName = APP_PACKAGE_NAME, profile = profile)

    @Test
    fun `pins stored Shortcuts after the Launchables load`() = databaseTest {
        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey())
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        createCoordinator(
            launchableState = launchableState,
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner))

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `reapplies a Locked Profile's pins when it becomes Unlocked`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createPrivateApp()
        val inputPrivateShortcut: ShortcutKey = ShortcutKey(
            packageName = PRIVATE_PACKAGE_NAME,
            id = FIRST_SHORTCUT_ID,
            profile = inputPrivateProfile.user
        )
        insertPopupShortcut(ownerKey = inputOwner.key, key = inputPrivateShortcut)
        val inputStatus: MutableStateFlow<PrivateSpaceStatus> = MutableStateFlow(
            createFixedPrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true)
        )
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(launchables = listOf(inputOwner))
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        createCoordinator(
            launchableState = launchableState,
            privateSpaceStatus = createPrivateSpaceStatusMock(status = inputStatus),
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        inputStatus.value = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner, inputPrivateApp))

        pinCalls.awaitPin(
            PinCall(
                packageName = PRIVATE_PACKAGE_NAME,
                profile = inputPrivateProfile.user,
                ids = setOf(FIRST_SHORTCUT_ID)
            )
        )
    }

    @Test
    fun `unions every pin source for the same package and Profile`() = databaseTest {
        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey())
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val extraSource: IShortcutPinSource = IShortcutPinSource { mapOf(appProfile to setOf(SECOND_SHORTCUT_ID)) }
        createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(IPopupShortcutPinSourceImpl(popupDao = database.getPopupDao()), extraSource)
        )
        finishPendingWork()

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `pins an added Shortcut item`() = databaseTest {
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()
        pinCalls.value = emptyList()

        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey())
        coordinator.reapplyPins(affectedProfiles = setOf(appProfile))

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `repins the remaining Shortcuts when one is removed`() = databaseTest {
        val firstKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)
        val secondKey: ShortcutKey = shortcutKey(id = SECOND_SHORTCUT_ID)
        insertPopupShortcut(ownerKey = inputOwner.key, key = firstKey, position = 0)
        insertPopupShortcut(ownerKey = inputOwner.key, key = secondKey, position = 1)
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        pinCalls.value = emptyList()

        database.getPopupDao().deleteItemsByKey(listOf(firstKey.format()))
        coordinator.reapplyPins(affectedProfiles = setOf(appProfile))

        pinCalls.awaitPin(pin(ids = setOf(SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(SECOND_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `keeps the union pinned across owners and unpins after the last is removed`() = databaseTest {
        val shortcutKey: ShortcutKey = shortcutKey()
        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey)
        insertPopupShortcut(ownerKey = inputOtherOwner.key, key = shortcutKey)
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        pinCalls.value = emptyList()

        database.getPopupDao().deleteItemsForOwner(inputOwner.key)
        coordinator.reapplyPins(affectedProfiles = setOf(appProfile))

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        pinCalls.value = emptyList()

        database.getPopupDao().deleteItemsForOwner(inputOtherOwner.key)
        coordinator.reapplyPins(affectedProfiles = setOf(appProfile))

        pinCalls.awaitPin(pin(ids = emptySet()))
        assertEquals(listOf(pin(ids = emptySet())), pinCalls.value)
    }

    @Test
    fun `deleteItemsByKey defers unpinning until the transaction commits`() = databaseTest {
        val firstKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)
        val secondKey: ShortcutKey = shortcutKey(id = SECOND_SHORTCUT_ID)
        insertPopupShortcut(ownerKey = inputOwner.key, key = firstKey, position = 0)
        insertPopupShortcut(ownerKey = inputOtherOwner.key, key = secondKey, position = 0)
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        database.getFolderDao().inTransaction {
            withContext(effects) {
                database.getPopupDao().deleteItemsByKey(listOf(firstKey.format(), secondKey.format()))
                coordinator.reapplyPins(affectedProfiles = setOf(appProfile))
            }
        }

        assertEquals(emptyList<PinCall>(), pinCalls.value)
        effects.run()
        pinCalls.awaitPin(pin(ids = emptySet()))
        assertEquals(listOf(pin(ids = emptySet())), pinCalls.value)
    }

    @Test
    fun `deleteOwner defers unpinning until the transaction commits`() = databaseTest {
        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey())
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        database.getFolderDao().inTransaction {
            withContext(effects) {
                database.getPopupDao().deleteItemsForOwner(inputOwner.key)
                coordinator.reapplyPins(affectedProfiles = setOf(appProfile))
            }
        }

        assertEquals(emptyList<PinCall>(), pinCalls.value)
        effects.run()
        pinCalls.awaitPin(pin(ids = emptySet()))
        assertEquals(listOf(pin(ids = emptySet())), pinCalls.value)
    }

    @Test
    fun `keeps pins when the transaction fails`() = databaseTest {
        val shortcutKey: ShortcutKey = shortcutKey()
        insertPopupShortcut(ownerKey = inputOwner.key, key = shortcutKey)
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        val actualFailure: Throwable? = runCatching {
            database.getFolderDao().inTransaction {
                withContext(effects) {
                    database.getPopupDao().deleteItemsByKey(listOf(shortcutKey.format()))
                    coordinator.reapplyPins(affectedProfiles = setOf(appProfile))
                    throw IllegalStateException("Folder delete failed")
                }
            }
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(emptyList<PinCall>(), pinCalls.value)
        assertEquals(1, database.getPopupDao().getAllItems().size)
    }

    @Test
    fun `stale cleanup unpins the Shortcuts it removes`() = databaseTest {
        val staleKey: ShortcutKey = shortcutKey(packageName = GONE_PACKAGE_NAME)
        insertPopupShortcut(ownerKey = inputOwner.key, key = staleKey)
        val launchableState: MutableStateFlow<LaunchableState> =
            MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputOwner)))
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            launchableState = launchableState,
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        createPopupRepository(
            popupDao = database.getPopupDao(),
            launchableRepository = createLaunchableRepositoryMock(launchableState),
            scope = backgroundScope,
            shortcutSource = createShortcutSourceMock(),
            widgetAreaRepository = createWidgetAreaRepositoryMock(),
            appWidgetHost = createAppWidgetHostMock(),
            shortcutPinCoordinator = coordinator
        )

        pinCalls.awaitPin(PinCall(packageName = GONE_PACKAGE_NAME, profile = profile, ids = emptySet()))
        assertTrue(database.getPopupDao().getAllItems().isEmpty())
    }

    @Test
    fun `removes a Shortcut once its Profile is no longer Kept and unpins it`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateShortcut: ShortcutKey = ShortcutKey(
            packageName = PRIVATE_PACKAGE_NAME,
            id = FIRST_SHORTCUT_ID,
            profile = inputPrivateProfile.user
        )
        insertPopupShortcut(ownerKey = inputOwner.key, key = inputPrivateShortcut)
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        val inputStatus: MutableStateFlow<PrivateSpaceStatus> = MutableStateFlow(
            createFixedPrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true)
        )
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(launchables = listOf(inputOwner), keptKeys = inputKeptKeys)
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            launchableState = launchableState,
            privateSpaceStatus = createPrivateSpaceStatusMock(status = inputStatus),
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        createPopupRepository(
            popupDao = database.getPopupDao(),
            launchableRepository = createLaunchableRepositoryMock(launchableState),
            scope = backgroundScope,
            shortcutSource = createShortcutSourceMock(),
            widgetAreaRepository = createWidgetAreaRepositoryMock(),
            appWidgetHost = createAppWidgetHostMock(),
            shortcutPinCoordinator = coordinator
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)
        assertEquals(1, database.getPopupDao().getAllItems().size)

        inputStatus.value = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner))

        pinCalls.awaitPin(PinCall(packageName = PRIVATE_PACKAGE_NAME, profile = inputPrivateProfile.user, ids = emptySet()))
        assertTrue(database.getPopupDao().getAllItems().isEmpty())
    }

    @Test
    fun `a concurrent reapply cannot drop a pin added inside the block`() = databaseTest {
        val pinSets: MutableMap<AppProfile, Set<String>> = mutableMapOf()
        val mockPinSource: IShortcutPinSource = IShortcutPinSource { pinSets.toMap() }
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(mockPinSource)
        )
        finishPendingWork()
        pinCalls.value = emptyList()
        val affectedProfiles: Set<AppProfile> = setOf(appProfile)
        val blockEntered: CompletableDeferred<Unit> = CompletableDeferred()
        val addPin: CompletableDeferred<Unit> = CompletableDeferred()
        val finishBlock: CompletableDeferred<Unit> = CompletableDeferred()

        backgroundScope.launch {
            coordinator.runUnderPinLock(affectedProfiles = affectedProfiles) {
                blockEntered.complete(Unit)
                addPin.await()
                pinSets[appProfile] = setOf(FIRST_SHORTCUT_ID)
                finishBlock.await()
            }
        }
        blockEntered.await()
        val concurrentReapply: Job = backgroundScope.launch { coordinator.reapplyPins(affectedProfiles = affectedProfiles) }
        runCurrent()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        addPin.complete(Unit)
        runCurrent()
        finishBlock.complete(Unit)
        concurrentReapply.join()
        finishPendingWork()

        assertEquals(2, pinCalls.value.size)
        assertTrue(pinCalls.value.all { call -> call.ids == setOf(FIRST_SHORTCUT_ID) })
    }

    @Test
    fun `runs the block and reapplies pins outside a transaction`() = databaseTest {
        val pinSets: MutableMap<AppProfile, Set<String>> = mutableMapOf()
        val mockPinSource: IShortcutPinSource = IShortcutPinSource { pinSets.toMap() }
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(mockPinSource)
        )
        finishPendingWork()
        pinCalls.value = emptyList()

        coordinator.runUnderPinLock(affectedProfiles = setOf(appProfile)) {
            pinSets[appProfile] = setOf(FIRST_SHORTCUT_ID)
        }

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `runs the block inside a transaction and reapplies pins after commit`() = databaseTest {
        val pinSets: MutableMap<AppProfile, Set<String>> = mutableMapOf()
        val mockPinSource: IShortcutPinSource = IShortcutPinSource { pinSets.toMap() }
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(mockPinSource)
        )
        finishPendingWork()
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        database.getFolderDao().inTransaction {
            withContext(effects) {
                coordinator.runUnderPinLock(affectedProfiles = setOf(appProfile)) {
                    pinSets[appProfile] = setOf(FIRST_SHORTCUT_ID)
                }
            }
        }

        assertEquals(emptyList<PinCall>(), pinCalls.value)
        effects.run()
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `reapplies pins when the block fails`() = databaseTest {
        val pinSets: MutableMap<AppProfile, Set<String>> = mutableMapOf()
        val mockPinSource: IShortcutPinSource = IShortcutPinSource { pinSets.toMap() }
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val coordinator: IShortcutPinCoordinator = createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(mockPinSource)
        )
        finishPendingWork()
        pinCalls.value = emptyList()

        val actualFailure: Throwable? = runCatching {
            coordinator.runUnderPinLock(affectedProfiles = setOf(appProfile)) {
                throw IllegalStateException("Store failed")
            }
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        pinCalls.awaitPin(pin(ids = emptySet()))
        assertEquals(listOf(pin(ids = emptySet())), pinCalls.value)
    }

    private fun TestScope.createCoordinator(
        launchableState: MutableStateFlow<LaunchableState> =
            MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputOwner))),
        privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatusMock(),
        mockShortcutSource: IShortcutSource = createShortcutSourceMock(),
        pinSources: List<IShortcutPinSource> = listOf(IPopupShortcutPinSourceImpl(popupDao = database.getPopupDao()))
    ): IShortcutPinCoordinator = createShortcutPinCoordinator(
        launchableRepository = createLaunchableRepositoryMock(launchableState),
        privateSpaceStatus = privateSpaceStatus,
        scope = backgroundScope,
        shortcutSource = mockShortcutSource,
        pinSources = pinSources
    )

    private suspend fun insertPopupShortcut(ownerKey: String, key: ShortcutKey, position: Int = 0) {
        database.getPopupDao().insertItems(listOf(PopupItemRecord(ownerKey = ownerKey, itemKey = key.format(), position = position)))
    }

    private fun shortcutKey(packageName: String = APP_PACKAGE_NAME, id: String = FIRST_SHORTCUT_ID): ShortcutKey =
        ShortcutKey(packageName = packageName, id = id, profile = profile)

    private fun pin(ids: Set<String>, packageName: String = APP_PACKAGE_NAME): PinCall =
        PinCall(packageName = packageName, profile = profile, ids = ids)
}
