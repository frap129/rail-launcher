package core.data.launchables

import android.content.ComponentName
import core.data.apps.model.App
import core.data.launchables.model.LaunchableGroup
import core.data.profiles.ProfileKind
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.shortcuts.ShortcutKey
import core.data.testing.MISSING_PRIVATE_USER_ID
import core.data.testing.MISSING_WORK_USER_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createLaunchableState
import core.data.testing.createProfileInfo
import core.data.testing.createRailLaunchable
import core.data.testing.createUserHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val SHORTCUT_ID: String = "shortcut-id"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class StoredLaunchableKeysTest {
    private val inputProfile = createUserHandle(userId = PRIVATE_USER_ID)
    private val inputToken: ProfileToken = inputProfile.toProfileToken()

    @Test
    fun `reads the Profile token of an activity App key`() {
        assertEquals(inputToken, "com.example.app/com.example.app.MainActivity_$inputProfile".profileToken())
    }

    @Test
    fun `reads the Profile token of a Legacy App key`() {
        assertEquals(inputToken, "com.example.app_$inputProfile".profileToken())
    }

    @Test
    fun `reads the Profile token of a Shortcut key`() {
        val inputKey: String = ShortcutKey(packageName = APP_PACKAGE_NAME, id = SHORTCUT_ID, profile = inputProfile).format()

        assertEquals(inputToken, inputKey.profileToken())
    }

    @Test
    fun `returns no token for a key without a Profile`() {
        assertNull("rail://maps".profileToken())
        assertNull("com.example.app_gone".profileToken())
        assertNull("com.example.app_UserHandle{}".profileToken())
        assertNull("com.example.app_UserHandle{abc}".profileToken())
    }

    @Test
    fun `returns no token for a malformed Shortcut key`() {
        assertNull("shortcut:malformed".profileToken())
        assertNull("shortcut:com.example.app:shortcut-id".profileToken())
    }

    @Test
    fun `matches only keys of kept Profiles`() {
        val inputState: LaunchableState = createLaunchableState(groups = emptyList(), keptKeys = setOf(inputToken))

        assertTrue(inputState.isKeptKey("com.example.app_$inputProfile"))
        assertTrue(inputState.isKeptKey(ShortcutKey(packageName = APP_PACKAGE_NAME, id = SHORTCUT_ID, profile = inputProfile).format()))
        assertFalse(inputState.isKeptKey("com.example.app_${createUserHandle(userId = 10)}"))
    }

    @Test
    fun `resolves a Legacy App key to the package representative`() {
        val inputPolicyApp: App = createApp(packageName = APP_PACKAGE_NAME).copy(
            componentName = ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.PolicyManagementActivity")
        )
        val inputSetupApp: App = inputPolicyApp.copy(
            componentName = ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.SetupManagementLaunchActivity"),
            isPackageRepresentative = false
        )
        val inputState: LaunchableState = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputSetupApp, inputPolicyApp)))
        )

        assertEquals(inputPolicyApp.key, inputState.resolveKey("${APP_PACKAGE_NAME}_${inputPolicyApp.profile}"))
        assertEquals(inputSetupApp.key, inputState.resolveKey(inputSetupApp.key))
        assertNull(inputState.resolveKey("com.example.other_${inputPolicyApp.profile}"))
    }

    @Test
    fun `lists every stored key an App may appear under`() {
        val inputApp: App = createApp(packageName = APP_PACKAGE_NAME)
        val inputOtherActivity: App = inputApp.copy(
            componentName = ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.OtherActivity"),
            isPackageRepresentative = false
        )
        val inputRailLaunchable = createRailLaunchable(name = "Maps")
        val inputState: LaunchableState = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputApp, inputOtherActivity)))
        )

        assertEquals(listOf(inputApp.key, "${APP_PACKAGE_NAME}_${inputApp.profile}"), inputApp.storedKeys())
        assertEquals(listOf(inputOtherActivity.key), inputOtherActivity.storedKeys())
        assertEquals(listOf(inputRailLaunchable.key), inputRailLaunchable.storedKeys())
        assertEquals(listOf(inputApp.key, "${APP_PACKAGE_NAME}_${inputApp.profile}"), inputState.storedKeys(inputApp.key))
        assertEquals(listOf(inputOtherActivity.key), inputState.storedKeys(inputOtherActivity.key))
        assertEquals(listOf(inputRailLaunchable.key), inputState.storedKeys(inputRailLaunchable.key))
    }

    @Test
    fun `keeps a Legacy App key while its App is installed and cleans it once the App is gone`() {
        val inputApp: App = createApp(packageName = APP_PACKAGE_NAME)
        val inputState: LaunchableState = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputApp)))
        )
        val inputInstalledKey: String = "${APP_PACKAGE_NAME}_${inputApp.profile}"
        val inputGoneKey: String = "com.example.gone_${inputApp.profile}"

        assertEquals(listOf(inputGoneKey), inputState.staleKeys(listOf(inputInstalledKey, inputGoneKey)))
    }

    @Test
    fun `cleans restored keys whose Profiles are missing while keeping personal and Locked Private keys`() {
        val inputPersonalApp: App = createApp(
            packageName = "com.example.personal",
            profileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        )
        val inputWorkApp: App = createApp(
            packageName = "com.example.work",
            profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        )
        val inputMissingWorkApp: App = createApp(
            packageName = "com.example.old-work",
            profileInfo = createProfileInfo(userId = MISSING_WORK_USER_ID, kind = ProfileKind.Work)
        )
        val inputPrivateApp: App = createApp(
            packageName = "com.example.secret",
            profileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private, isQuiet = true)
        )
        val inputMissingPrivateApp: App = createApp(
            packageName = "com.example.old-private",
            profileInfo = createProfileInfo(userId = MISSING_PRIVATE_USER_ID, kind = ProfileKind.Private)
        )
        val inputState: LaunchableState = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputPersonalApp, inputWorkApp))),
            keptKeys = setOf(inputToken)
        )
        val inputKeys: List<String> = listOf(
            inputPersonalApp.key,
            inputWorkApp.key,
            inputMissingWorkApp.key,
            inputPrivateApp.key,
            inputMissingPrivateApp.key
        )
        val expectedStaleKeys: List<String> = listOf(inputMissingWorkApp.key, inputMissingPrivateApp.key)

        val actualStaleKeys: List<String> = inputState.staleKeys(inputKeys)

        assertEquals(expectedStaleKeys, actualStaleKeys)
    }
}
