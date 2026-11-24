package core.ui.model.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import core.ui.R

/**
 * A keyboard shortcut the launcher consumes: any of [keyCodes] held with one of [modifiers], which
 * the launcher activity turns into [signal].
 *
 * [label] names the action in the system keyboard shortcuts helper and, later, the Keyboard
 * shortcuts help sheet, so a new shortcut is only added to this list. Digit shortcuts publish both
 * their primary and numpad key codes.
 */
enum class KeyboardShortcut(@StringRes val label: Int, val keyCodes: List<Int>, val signal: LauncherSignal) {
    Search(label = R.string.search, keyCodes = listOf(KeyEvent.KEYCODE_F), signal = LauncherSignal.OpenSearch),
    Agenda(label = R.string.agenda, keyCodes = listOf(KeyEvent.KEYCODE_A), signal = LauncherSignal.OpenAgenda),
    Settings(label = R.string.settings, keyCodes = listOf(KeyEvent.KEYCODE_S), signal = LauncherSignal.OpenSettings),
    OpenRow1(index = 0),
    OpenRow2(index = 1),
    OpenRow3(index = 2),
    OpenRow4(index = 3),
    OpenRow5(index = 4),
    OpenRow6(index = 5),
    OpenRow7(index = 6),
    OpenRow8(index = 7),
    OpenRow9(index = 8);

    private constructor(index: Int) : this(
        label = R.string.open_row,
        keyCodes = listOf(KeyEvent.KEYCODE_1 + index, KeyEvent.KEYCODE_NUMPAD_1 + index),
        signal = LauncherSignal.OpenIndex(index = index)
    )

    companion object {
        /** The modifier meta states every shortcut accepts: Ctrl alone or Meta alone. */
        val modifiers: List<Int> = listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)

        /**
         * The listed shortcut [event] presses with [keyCode], or null when it presses any other
         * key, or any combination other than one of [modifiers] alone.
         */
        fun forEvent(keyCode: Int, event: KeyEvent): KeyboardShortcut? {
            if (!event.pressesOneShortcutModifier()) {
                return null
            }
            return entries.firstOrNull { shortcut -> keyCode in shortcut.keyCodes }
        }

        private fun KeyEvent.pressesOneShortcutModifier(): Boolean {
            val pressedModifiers: Int = KeyboardShortcut.modifiers.count { modifier -> metaState and modifier != 0 }
            return pressedModifiers == 1 && !isAltPressed && !isShiftPressed
        }
    }
}

/**
 * Whether [this] event presses Ctrl, Alt or Meta, which a Typed character or a Row focus key never
 * accepts.
 */
fun KeyEvent.hasShortcutModifier(): Boolean = isCtrlPressed || isAltPressed || isMetaPressed
