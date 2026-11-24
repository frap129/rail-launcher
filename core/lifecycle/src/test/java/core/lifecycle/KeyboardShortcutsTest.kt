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
        val expectedRowModifiers: List<Int> = listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)
        val expectedShortcutCount: Int = KeyboardShortcut.entries.sumOf { shortcut ->
            shortcut.keyCodes.size * shortcut.modifierStates.size
        }
        assertEquals(inputGroupLabel, actualGroup.label.toString())
        assertEquals(expectedShortcutCount, actualGroup.items.size)
        assertEquals(
            expectedDigitKeyCodes.size * expectedRowModifiers.size,
            actualGroup.items.count { info -> info.keycode in expectedDigitKeyCodes }
        )
        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                val actualShortcutInfos: List<KeyboardShortcutInfo> =
                    actualGroup.items.filter { info -> info.keycode == inputKeyCode }
                assertEquals(inputShortcut.modifierStates.toSet(), actualShortcutInfos.map { info -> info.modifiers }.toSet())
                assertTrue(actualShortcutInfos.all { info -> info.label.toString() == inputShortcut.name })
            }
        }
    }

    @Test
    fun `lists shifted and unshifted help shortcuts`() {
        val inputGroupLabel: String = "Rail"
        val actualGroup: KeyboardShortcutGroup = createKeyboardShortcutGroup(
            label = inputGroupLabel,
            resolveLabel = { shortcut -> shortcut.name }
        )
        val actualHelpShortcuts: List<KeyboardShortcutInfo> = actualGroup.items.filter { info ->
            info.keycode == KeyEvent.KEYCODE_SLASH
        }
        val expectedModifiers: Set<Int> = setOf(
            KeyEvent.META_CTRL_ON,
            KeyEvent.META_META_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON
        )
        assertEquals(expectedModifiers, actualHelpShortcuts.map { info -> info.modifiers }.toSet())
        assertTrue(actualHelpShortcuts.all { info -> info.label.toString() == KeyboardShortcut.KeyboardShortcutsHelp.name })
    }

    @Test
    fun `lists unmodified Tab`() {
        val inputGroupLabel: String = "Rail"
        val actualGroup: KeyboardShortcutGroup = createKeyboardShortcutGroup(
            label = inputGroupLabel,
            resolveLabel = { shortcut -> shortcut.name }
        )
        val actualTabShortcuts: List<KeyboardShortcutInfo> = actualGroup.items.filter { info -> info.keycode == KeyEvent.KEYCODE_TAB }
        assertEquals(1, actualTabShortcuts.size)
        val actualTabShortcut: KeyboardShortcutInfo = actualTabShortcuts.single()
        assertEquals(KeyboardShortcut.ToggleAppList.name, actualTabShortcut.label.toString())
        assertEquals(0, actualTabShortcut.modifiers)
    }
}
