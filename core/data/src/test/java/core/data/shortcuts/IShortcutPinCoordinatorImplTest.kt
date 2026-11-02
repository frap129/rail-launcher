package core.data.shortcuts

import android.os.UserHandle
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.LaunchableState
import core.data.popups.PopupItemRecord
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PRIVATE_PACKAGE_NAME
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.awaitPin
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import core.data.testing.createShortcutPinCoordinator
import core.data.testing.createShortcutSourceMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val TEST_USER_ID: Int = 0
private val inputOwner: App = createApp()

@OptIn(ExperimentalCoroutinesApi::class)
class IShortcutPinCoordinatorImplTest : InMemoryDatabaseTest() {
    private val profile: UserHandle = UserHandle.getUserHandleForUid(TEST_USER_ID)

    @Test
    fun `pins stored Shortcuts after the Launchables load`() = databaseTest {
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        database.getPopupDao().insertItems(
            listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = shortcutKey.format(), position = 0))
        )
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
        database.getPopupDao().insertItems(
            listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputPrivateShortcut.format(), position = 0))
        )
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(launchables = listOf(inputOwner), keptKeys = inputKeptKeys)
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        createCoordinator(
            launchableState = launchableState,
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        launchableState.value = createLoadedLaunchableState(
            launchables = listOf(inputOwner, inputPrivateApp),
            enumeratedKeys = inputKeptKeys
        )

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
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        database.getPopupDao().insertItems(
            listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val extraSource: IShortcutPinSource = IShortcutPinSource {
            mapOf(AppProfile(packageName = APP_PACKAGE_NAME, profile = profile) to setOf(SECOND_SHORTCUT_ID))
        }
        createCoordinator(
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls),
            pinSources = listOf(extraSource)
        )
        finishPendingWork()

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID))), pinCalls.value)
    }

    private fun TestScope.createCoordinator(
        launchableState: MutableStateFlow<LaunchableState> =
            MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputOwner))),
        mockShortcutSource: IShortcutSource = createShortcutSourceMock(),
        pinSources: List<IShortcutPinSource> = emptyList()
    ): IShortcutPinCoordinator = createShortcutPinCoordinator(
        popupDao = database.getPopupDao(),
        launchableRepository = createLaunchableRepositoryMock(launchableState),
        scope = backgroundScope,
        shortcutSource = mockShortcutSource,
        pinSources = pinSources
    )

    private fun pin(ids: Set<String>, packageName: String = APP_PACKAGE_NAME): PinCall =
        PinCall(packageName = packageName, profile = profile, ids = ids)
}
