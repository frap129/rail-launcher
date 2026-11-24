package core.ui.model.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import core.ui.R

/**
 * A keyboard shortcut the launcher consumes: any of [keyCodes] with one of [modifiers], where zero
 * means no modifier, which the launcher activity turns into [signal].
 *
 * [label] names the action in the system keyboard shortcuts helper and, later, the Keyboard
 * shortcuts help sheet, so a new shortcut is only added to this list. Digit shortcuts publish both
 * their primary and numpad key codes.
 */
enum class KeyboardShortcut(
    @StringRes val label: Int,
    val keyCodes: List<Int>,
    val signal: LauncherSignal,
    val modifiers: List<Int> = listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)
) {
    Search(label = R.string.search, keyCodes = listOf(KeyEvent.KEYCODE_F), signal = LauncherSignal.OpenSearch),
    Agenda(label = R.string.agenda, keyCodes = listOf(KeyEvent.KEYCODE_A), signal = LauncherSignal.OpenAgenda),
    Settings(label = R.string.settings, keyCodes = listOf(KeyEvent.KEYCODE_S), signal = LauncherSignal.OpenSettings),
    ToggleAppList(
        label = R.string.toggle_app_list,
        keyCodes = listOf(KeyEvent.KEYCODE_TAB),
        signal = LauncherSignal.ToggleAppList,
        modifiers = listOf(0)
    ),
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
        /**
         * The listed shortcut [event] presses with [keyCode], or null when it presses any other
         * key or a modifier combination that shortcut does not list.
         */
        fun forEvent(keyCode: Int, event: KeyEvent): KeyboardShortcut? = entries.firstOrNull { shortcut ->
            keyCode in shortcut.keyCodes &&
                shortcut.modifiers.any { expectedModifiers: Int ->
                    event.matchesShortcutModifiers(expectedModifiers = expectedModifiers)
                }
        }

        private fun KeyEvent.matchesShortcutModifiers(expectedModifiers: Int): Boolean {
            val pressedModifiers: Int =
                (if (isCtrlPressed) KeyEvent.META_CTRL_ON else 0) or
                    (if (isMetaPressed) KeyEvent.META_META_ON else 0)
            return pressedModifiers == expectedModifiers && !isAltPressed && !isShiftPressed
        }
    }
}

/**
 * Whether [this] event presses Ctrl, Alt or Meta, which a Typed character or a Row focus key never
 * accepts.
 */
fun KeyEvent.hasShortcutModifier(): Boolean = isCtrlPressed || isAltPressed || isMetaPressed
