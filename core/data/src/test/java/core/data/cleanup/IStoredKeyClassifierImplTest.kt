package core.data.cleanup

import core.data.launchables.LaunchableState
import core.data.profiles.ProfileKind
import core.data.profiles.toProfileToken
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutResolution
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createProfileInfo
import core.data.testing.createShortcut
import core.data.testing.createShortcutSourceMock
import core.data.testing.createUserHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val POPUP_PACKAGE_NAME: String = "com.example.popup"
private const val PRIVATE_PACKAGE_NAME: String = "com.example.private"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IStoredKeyClassifierImplTest {
    private val inputProfile = createUserHandle(userId = PERSONAL_USER_ID)

    @Test
    fun `emits null until the Launchables load`() = runTest {
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = emptyList(), isLoaded = false))
        val classifier: IStoredKeyClassifier = createClassifier(state = inputState)

        val actualStates: Map<String, StoredKeyState>? = classifier.observe(flowOf(setOf("key"))).first()

        assertNull(actualStates)
    }

    @Test
    fun `classifies a present Launchable and a present Shortcut`() = runTest {
        val inputApp = createApp(packageName = APP_PACKAGE_NAME)
        val inputShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val inputShortcut = createShortcut(packageName = APP_PACKAGE_NAME, profile = inputProfile)
        val classifier: IStoredKeyClassifier = createClassifier(
            state = MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputApp))),
            resolution = MutableStateFlow(ShortcutResolution(shortcuts = mapOf(inputShortcutKey to inputShortcut)))
        )

        val actualStates: Map<String, StoredKeyState> = requireNotNull(
            classifier.observe(flowOf(setOf(inputApp.key, inputShortcutKey.format()))).first()
        )

        assertEquals(StoredKeyState.Present(launchable = inputApp), actualStates[inputApp.key])
        assertEquals(StoredKeyState.Present(launchable = inputShortcut), actualStates[inputShortcutKey.format()])
    }

    @Test
    fun `classifies kept, retained and unresolved Shortcut keys`() = runTest {
        val inputPrivateProfile = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp = createApp(packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputRetainedKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val inputPopupApp = createApp(packageName = POPUP_PACKAGE_NAME)
        val inputPopupKey = ShortcutKey(packageName = POPUP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val inputState: LaunchableState = createLoadedLaunchableState(
            launchables = listOf(inputPopupApp),
            keptKeys = setOf(inputPrivateProfile.user.toProfileToken()),
            retainedKeys = setOf(inputRetainedKey.format())
        )
        val classifier: IStoredKeyClassifier = createClassifier(state = MutableStateFlow(inputState))

        val actualStates: Map<String, StoredKeyState> = requireNotNull(
            classifier.observe(flowOf(setOf(inputPrivateApp.key, inputRetainedKey.format(), inputPopupKey.format()))).first()
        )

        assertEquals(StoredKeyState.Kept, actualStates[inputPrivateApp.key])
        assertEquals(StoredKeyState.Unresolved, actualStates[inputRetainedKey.format()])
        assertEquals(StoredKeyState.Unresolved, actualStates[inputPopupKey.format()])
    }

    @Test
    fun `classifies gone Shortcuts, missing Apps and missing Launchables as stale`() = runTest {
        val inputInstalledApp = createApp(packageName = APP_PACKAGE_NAME)
        val inputGoneKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val inputMissingAppKey = ShortcutKey(packageName = "com.example.missing", id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val classifier: IStoredKeyClassifier = createClassifier(
            state = MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputInstalledApp))),
            resolution = MutableStateFlow(ShortcutResolution(goneKeys = setOf(inputGoneKey)))
        )

        val actualStates: Map<String, StoredKeyState> = requireNotNull(
            classifier.observe(flowOf(setOf(inputGoneKey.format(), inputMissingAppKey.format(), "com.example.gone"))).first()
        )

        assertEquals(StoredKeyState.Stale, actualStates[inputGoneKey.format()])
        assertEquals(StoredKeyState.Stale, actualStates[inputMissingAppKey.format()])
        assertEquals(StoredKeyState.Stale, actualStates["com.example.gone"])
    }

    @Test
    fun `trusts a resolved Shortcut even when its publisher has no launcher activity`() = runTest {
        val inputShortcutKey = ShortcutKey(packageName = "com.example.site", id = FIRST_SHORTCUT_ID, profile = inputProfile)
        val inputShortcut = createShortcut(packageName = "com.example.site", profile = inputProfile)
        val classifier: IStoredKeyClassifier = createClassifier(
            state = MutableStateFlow(createLoadedLaunchableState(launchables = emptyList())),
            resolution = MutableStateFlow(ShortcutResolution(shortcuts = mapOf(inputShortcutKey to inputShortcut)))
        )

        val actualStates: Map<String, StoredKeyState> = requireNotNull(
            classifier.observe(flowOf(setOf(inputShortcutKey.format()))).first()
        )

        assertEquals(StoredKeyState.Present(launchable = inputShortcut), actualStates[inputShortcutKey.format()])
    }

    @Test
    fun `classifies a restored key without a Launchable as stale`() = runTest {
        val classifier: IStoredKeyClassifier =
            createClassifier(state = MutableStateFlow(createLoadedLaunchableState(launchables = emptyList())))

        val actualStates: Map<String, StoredKeyState> = requireNotNull(
            classifier.observe(flowOf(setOf("com.example.restored"))).first()
        )

        assertEquals(StoredKeyState.Stale, actualStates["com.example.restored"])
    }

    private fun createClassifier(
        state: MutableStateFlow<LaunchableState>,
        resolution: MutableStateFlow<ShortcutResolution> = MutableStateFlow(ShortcutResolution())
    ): IStoredKeyClassifier = IStoredKeyClassifierImpl(
        launchableRepository = createLaunchableRepositoryMock(state),
        shortcutSource = createShortcutSourceMock(resolution = resolution)
    )
}
