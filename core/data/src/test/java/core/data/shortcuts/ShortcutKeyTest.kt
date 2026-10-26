package core.data.shortcuts

import android.os.UserHandle
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PACKAGE_NAME: String = "com.example.app"
private const val SHORTCUT_ID: String = "compose/message_new_topic:now_日本語"
private const val SECOND_USER_ID: Int = 10
private const val USER_ID_OFFSET: Int = 100_000

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ShortcutKeyTest {
    private val inputProfile: UserHandle = UserHandle.getUserHandleForUid(SECOND_USER_ID * USER_ID_OFFSET)

    @Test
    fun `round-trips an id with a slash, underscore, colon and non-ASCII characters`() {
        val inputKey: ShortcutKey = ShortcutKey(packageName = PACKAGE_NAME, id = SHORTCUT_ID, profile = inputProfile)
        val actualKey: String = inputKey.format()
        assertEquals(inputKey, ShortcutKey.parse(actualKey))
    }

    @Test
    fun `formats the package, encoded id and profile`() {
        val inputKey: ShortcutKey = ShortcutKey(packageName = PACKAGE_NAME, id = SHORTCUT_ID, profile = inputProfile)
        assertEquals(
            "shortcut:com.example.app:compose%2Fmessage_new_topic%3Anow_%E6%97%A5%E6%9C%AC%E8%AA%9E:UserHandle{$SECOND_USER_ID}",
            inputKey.format()
        )
    }

    @Test
    fun `rejects a key without the shortcut prefix`() {
        assertNull(ShortcutKey.parse("app:com.example.app:id:UserHandle{0}"))
    }

    @Test
    fun `rejects a key without a profile`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id"))
    }

    @Test
    fun `parses a key with a UserHandle profile`() {
        val expectedKey = ShortcutKey(packageName = PACKAGE_NAME, id = SHORTCUT_ID, profile = inputProfile)
        val actualKey: ShortcutKey? = ShortcutKey.parse(
            "shortcut:com.example.app:compose%2Fmessage_new_topic%3Anow_%E6%97%A5%E6%9C%AC%E8%AA%9E:UserHandle{10}"
        )
        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `rejects a key with a malformed profile`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id:profile-0"))
    }

    @Test
    fun `rejects a key with an empty user id`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id:UserHandle{}"))
    }

    @Test
    fun `rejects a key with a non-numeric user id`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id:UserHandle{abc}"))
    }

    @Test
    fun `rejects a key with a missing closing brace`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id:UserHandle{10"))
    }

    @Test
    fun `rejects a key with a negative user id`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:id:UserHandle{-1}"))
    }

    @Test
    fun `rejects a key with a blank package`() {
        assertNull(ShortcutKey.parse("shortcut::id:UserHandle{0}"))
    }

    @Test
    fun `rejects a key with an empty id`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app::UserHandle{0}"))
    }

    @Test
    fun `rejects a key with malformed URL encoding`() {
        assertNull(ShortcutKey.parse("shortcut:com.example.app:bad%ZZid:UserHandle{0}"))
    }
}
