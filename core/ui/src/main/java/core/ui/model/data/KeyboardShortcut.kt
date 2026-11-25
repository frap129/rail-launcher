package core.ui.model.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import core.ui.R

private const val NO_KEYBOARD_MODIFIERS: Int = 0

/** A modifier accepted by a [KeyboardHelpEntry], with the state Android's key APIs expect. */
enum class KeyboardShortcutModifier(val metaState: Int) {
    None(NO_KEYBOARD_MODIFIERS),
    Control(KeyEvent.META_CTRL_ON),
    Meta(KeyEvent.META_META_ON)
}

/**
 * A key combination or unmodified Tab that fires a [signal] on Home or in the App List.
 *
 * [label] names the action in the system keyboard shortcuts helper and the Keyboard shortcuts help
 * sheet, with [labelArguments] supplying its format arguments. Digit shortcuts publish both their
 * primary and numpad key codes. [isShiftAllowed] adds shifted variants for layouts that need Shift
 * to type the key. [keyLabel] overrides the generated key label when one row represents many keys.
 */
enum class KeyboardShortcut(
    @get:StringRes override val label: Int,
    override val keyCodes: List<Int>,
    val signal: LauncherSignal,
    override val modifiers: List<KeyboardShortcutModifier> = listOf(KeyboardShortcutModifier.Control, KeyboardShortcutModifier.Meta),
    override val isShiftAllowed: Boolean = false,
    override val labelArguments: List<String> = emptyList(),
    @get:StringRes override val keyLabel: Int? = null
) : KeyboardHelpEntry {
    Search(label = R.string.search, keyCodes = listOf(KeyEvent.KEYCODE_F), signal = LauncherSignal.OpenSearch),
    Agenda(label = R.string.agenda, keyCodes = listOf(KeyEvent.KEYCODE_A), signal = LauncherSignal.OpenAgenda),
    Settings(label = R.string.settings, keyCodes = listOf(KeyEvent.KEYCODE_S), signal = LauncherSignal.OpenSettings),
    KeyboardShortcutsHelp(
        label = R.string.keyboard_shortcuts_help,
        keyCodes = listOf(KeyEvent.KEYCODE_SLASH),
        signal = LauncherSignal.OpenKeyboardShortcutsHelp,
        modifiers = listOf(KeyboardShortcutModifier.Control),
        isShiftAllowed = true
    ),
    ToggleAppList(
        label = R.string.toggle_app_list,
        keyCodes = listOf(KeyEvent.KEYCODE_TAB),
        signal = LauncherSignal.ToggleAppList,
        modifiers = listOf(KeyboardShortcutModifier.None)
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
        signal = LauncherSignal.OpenIndex(index = index),
        labelArguments = listOf((index + 1).toString())
    )

    companion object {
        /**
         * The listed shortcut [event] presses with [keyCode], or null when it presses any other
         * key or a modifier combination that shortcut does not list.
         */
        fun forEvent(keyCode: Int, event: KeyEvent): KeyboardShortcut? = entries.firstOrNull { shortcut: KeyboardShortcut ->
            keyCode in shortcut.keyCodes &&
                shortcut.modifierStates.any { expectedModifiers: Int ->
                    event.matchesShortcutModifiers(expectedModifiers = expectedModifiers)
                }
        }

        private fun KeyEvent.matchesShortcutModifiers(expectedModifiers: Int): Boolean {
            val pressedModifiers: Int =
                (if (isCtrlPressed) KeyEvent.META_CTRL_ON else 0) or
                    (if (isMetaPressed) KeyEvent.META_META_ON else 0) or
                    (if (isShiftPressed) KeyEvent.META_SHIFT_ON else 0)
            return pressedModifiers == expectedModifiers && !isAltPressed
        }
    }
}

/**
 * Whether [this] event presses Ctrl, Alt or Meta, which a Typed character or a Row focus key never
 * accepts.
 */
fun KeyEvent.hasShortcutModifier(): Boolean = isCtrlPressed || isAltPressed || isMetaPressed
