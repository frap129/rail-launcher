package core.lifecycle

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import core.ui.model.data.KeyboardShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardShortcutsTest {
    @Test
    fun `lists every shortcut key code for each of its modifiers`() {
        val inputGroupLabel: String = "Rail"
        val actualGroup: KeyboardShortcutGroup = createKeyboardShortcutGroup(
            label = inputGroupLabel,
            resolveLabel = { shortcut -> shortcut.name }
        )
        val expectedDigitKeyCodes: Set<Int> = (KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9).toSet() +
            (KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9).toSet()
        val expectedShortcutCount: Int = KeyboardShortcut.entries.sumOf { shortcut -> shortcut.keyCodes.size } *
            KeyboardShortcut.modifiers.size
        assertEquals(inputGroupLabel, actualGroup.label.toString())
        assertEquals(expectedShortcutCount, actualGroup.items.size)
        assertEquals(
            expectedDigitKeyCodes.size * KeyboardShortcut.modifiers.size,
            actualGroup.items.count { info -> info.keycode in expectedDigitKeyCodes }
        )
        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                val actualShortcutInfos: List<KeyboardShortcutInfo> =
                    actualGroup.items.filter { info -> info.keycode == inputKeyCode }
                assertEquals(KeyboardShortcut.modifiers.toSet(), actualShortcutInfos.map { info -> info.modifiers }.toSet())
                assertTrue(actualShortcutInfos.all { info -> info.label.toString() == inputShortcut.name })
            }
        }
    }
}
