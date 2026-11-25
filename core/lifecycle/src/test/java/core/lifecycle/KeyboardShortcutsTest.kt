package core.lifecycle

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import core.ui.model.data.KeyboardHelpEntry
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.keyboardHelpEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardShortcutsTest {
    private val inputGroupLabel: String = "Rail"

    @Test
    fun `lists every help entry key code for each of its modifiers`() {
        val actualGroup: KeyboardShortcutGroup = createGroup()
        val expectedDigitKeyCodes: Set<Int> = (KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9).toSet() +
            (KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9).toSet()
        val expectedRowModifiers: List<Int> = listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)
        val expectedModifiedDigitCount: Int = expectedDigitKeyCodes.size * expectedRowModifiers.size
        val expectedUnmodifiedDigitCount: Int = (KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9).count()
        val expectedHelpEntryCount: Int = 89
        assertEquals(inputGroupLabel, actualGroup.label.toString())
        assertEquals(expectedHelpEntryCount, actualGroup.items.size)
        assertEquals(
            expectedModifiedDigitCount + expectedUnmodifiedDigitCount,
            actualGroup.items.count { actualInfo: KeyboardShortcutInfo -> actualInfo.keycode in expectedDigitKeyCodes }
        )
        keyboardHelpEntries.forEach { inputEntry: KeyboardHelpEntry ->
            inputEntry.keyCodes.forEach { inputKeyCode: Int ->
                val actualHelpEntryInfos: List<KeyboardShortcutInfo> = actualGroup.items.filter { actualInfo: KeyboardShortcutInfo ->
                    actualInfo.keycode == inputKeyCode && actualInfo.label.toString() == inputEntry.toString()
                }
                assertEquals(
                    inputEntry.modifierStates.toSet(),
                    actualHelpEntryInfos.map { actualInfo: KeyboardShortcutInfo -> actualInfo.modifiers }.toSet()
                )
            }
        }
    }

    @Test
    fun `lists shifted and unshifted Ctrl help shortcuts only`() {
        val actualGroup: KeyboardShortcutGroup = createGroup()
        val actualHelpShortcuts: List<KeyboardShortcutInfo> = actualGroup.items.filter { actualInfo: KeyboardShortcutInfo ->
            actualInfo.keycode == KeyEvent.KEYCODE_SLASH
        }
        val expectedModifiers: Set<Int> = setOf(
            KeyEvent.META_CTRL_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        )
        assertEquals(expectedModifiers, actualHelpShortcuts.map { actualInfo: KeyboardShortcutInfo -> actualInfo.modifiers }.toSet())
        assertTrue(
            actualHelpShortcuts.all { actualInfo: KeyboardShortcutInfo ->
                actualInfo.label.toString() == KeyboardShortcut.KeyboardShortcutsHelp.name
            }
        )
    }

    @Test
    fun `lists unmodified Search typing Back and Row focus keys`() {
        val actualGroup: KeyboardShortcutGroup = createGroup()
        val expectedLabelsByKeyCode: Map<Int, String> = buildMap {
            (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).forEach { inputKeyCode: Int -> put(inputKeyCode, "StartSearch") }
            (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).forEach { inputKeyCode: Int -> put(inputKeyCode, "StartSearch") }
            put(KeyEvent.KEYCODE_ESCAPE, "Back")
            listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT
            ).forEach { inputKeyCode: Int -> put(inputKeyCode, "MoveRowFocus") }
            listOf(
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_CENTER
            ).forEach { inputKeyCode: Int -> put(inputKeyCode, "OpenFocusedRow") }
        }
        val actualLabelsByKeyCode: Map<Int, String> = actualGroup.items
            .filter { actualInfo: KeyboardShortcutInfo ->
                actualInfo.keycode in expectedLabelsByKeyCode && actualInfo.modifiers == 0
            }
            .associate { actualInfo: KeyboardShortcutInfo -> actualInfo.keycode to actualInfo.label.toString() }
        assertEquals(expectedLabelsByKeyCode, actualLabelsByKeyCode)
    }

    @Test
    fun `lists unmodified Tab`() {
        val actualGroup: KeyboardShortcutGroup = createGroup()
        val actualTabShortcuts: List<KeyboardShortcutInfo> = actualGroup.items.filter { actualInfo: KeyboardShortcutInfo ->
            actualInfo.keycode == KeyEvent.KEYCODE_TAB
        }
        assertEquals(1, actualTabShortcuts.size)
        val actualTabShortcut: KeyboardShortcutInfo = actualTabShortcuts.single()
        assertEquals(KeyboardShortcut.ToggleAppList.name, actualTabShortcut.label.toString())
        assertEquals(0, actualTabShortcut.modifiers)
    }

    private fun createGroup(): KeyboardShortcutGroup = createKeyboardShortcutGroup(
        label = inputGroupLabel,
        resolveLabel = { inputEntry: KeyboardHelpEntry -> inputEntry.toString() }
    )
}
