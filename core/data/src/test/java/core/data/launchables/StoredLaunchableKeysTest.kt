package core.data.launchables

import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.shortcuts.ShortcutKey
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createLaunchableState
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
    fun `reads the Profile token of an App key`() {
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
}
