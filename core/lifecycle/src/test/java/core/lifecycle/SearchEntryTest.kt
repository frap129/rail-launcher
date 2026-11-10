package core.lifecycle

import android.content.Intent
import android.net.Uri
import android.view.KeyCharacterMap
import android.view.KeyEvent
import core.ui.model.data.SEARCH_ENTRY_URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchEntryTest {
    @Test
    fun `the documented Rail URI is the Search entry`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse(SEARCH_ENTRY_URI))

        assertTrue(inputIntent.isSearchEntry())
    }

    @Test
    fun `a Search entry with a path is not the Search entry`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse("$SEARCH_ENTRY_URI/other"))

        assertFalse(inputIntent.isSearchEntry())
    }

    @Test
    fun `a Rail intent to another host is not the Search entry`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse("rail-launcher://settings/main"))

        assertFalse(inputIntent.isSearchEntry())
    }

    @Test
    fun `an intent from another scheme is not the Search entry`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://search"))

        assertFalse(inputIntent.isSearchEntry())
    }

    @Test
    fun `a HOME intent is not the Search entry`() {
        val inputIntent: Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        assertFalse(inputIntent.isSearchEntry())
    }

    @Test
    fun `a printable hardware key types its character`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_A)

        assertEquals("a", inputEvent.typedCharacter())
    }

    @Test
    fun `a Ctrl shortcut types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_A, metaState = KeyEvent.META_CTRL_ON)

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `an Alt shortcut types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_A, metaState = KeyEvent.META_ALT_ON)

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `a Meta shortcut types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_A, metaState = KeyEvent.META_META_ON)

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `a soft keyboard key types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_A, flags = KeyEvent.FLAG_SOFT_KEYBOARD)

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `a virtual keyboard key types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(
            keyCode = KeyEvent.KEYCODE_A,
            deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD
        )

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `a dead key types nothing`() {
        val inputCodePoint: Int = KeyCharacterMap.COMBINING_ACCENT or 'e'.code

        assertNull(printableHardwareCharacter(codePoint = inputCodePoint, deviceId = 0, flags = 0))
    }

    @Test
    fun `a whitespace key types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_SPACE)

        assertNull(inputEvent.typedCharacter())
    }

    @Test
    fun `a non-printing key types nothing`() {
        val inputEvent: KeyEvent = createKeyEvent(keyCode = KeyEvent.KEYCODE_ENTER)

        assertNull(inputEvent.typedCharacter())
    }

    private fun createKeyEvent(keyCode: Int, metaState: Int = 0, deviceId: Int = 0, flags: Int = 0): KeyEvent = KeyEvent(
        0L,
        0L,
        KeyEvent.ACTION_DOWN,
        keyCode,
        0,
        metaState,
        deviceId,
        0,
        flags
    )
}
