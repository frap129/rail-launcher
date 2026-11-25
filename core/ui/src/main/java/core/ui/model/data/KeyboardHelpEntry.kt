package core.ui.model.data

import android.content.res.Resources
import android.view.KeyEvent
import androidx.annotation.StringRes
import core.ui.R

/** One input listed in the Keyboard shortcuts help sheet and the system keyboard shortcuts helper. */
interface KeyboardHelpEntry {
    @get:StringRes val label: Int
    val keyCodes: List<Int>
    val modifiers: List<KeyboardShortcutModifier>
    val isShiftAllowed: Boolean
    val labelArguments: List<String>

    @get:StringRes val keyLabel: Int?

    /** Every exact modifier state this entry accepts, including optional Shift variants. */
    val modifierStates: List<Int>
        get() = modifiers.flatMap { modifier: KeyboardShortcutModifier ->
            if (isShiftAllowed) {
                listOf(modifier.metaState, modifier.metaState or KeyEvent.META_SHIFT_ON)
            } else {
                listOf(modifier.metaState)
            }
        }.distinct()
}

/** Inputs listed for help that are handled outside the Launcher signal Keyboard shortcuts. */
enum class KeyboardInputAction(
    @get:StringRes override val label: Int,
    override val keyCodes: List<Int>,
    override val modifiers: List<KeyboardShortcutModifier>,
    override val isShiftAllowed: Boolean = false,
    override val labelArguments: List<String> = emptyList(),
    @get:StringRes override val keyLabel: Int? = null
) : KeyboardHelpEntry {
    StartSearch(
        label = R.string.start_search,
        keyCodes = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toList(),
        modifiers = listOf(KeyboardShortcutModifier.None),
        keyLabel = R.string.keyboard_shortcut_alphanumeric
    ),
    Back(
        label = R.string.back,
        keyCodes = listOf(KeyEvent.KEYCODE_ESCAPE),
        modifiers = listOf(KeyboardShortcutModifier.None),
        keyLabel = R.string.keyboard_shortcut_escape
    ),
    MoveRowFocus(
        label = R.string.move_row_focus,
        keyCodes = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT
        ),
        modifiers = listOf(KeyboardShortcutModifier.None),
        keyLabel = R.string.keyboard_shortcut_arrow_keys
    ),
    OpenFocusedRow(
        label = R.string.open_focused_row,
        keyCodes = listOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER
        ),
        modifiers = listOf(KeyboardShortcutModifier.None),
        keyLabel = R.string.keyboard_shortcut_enter_keys
    )
}

/** The single ordered list feeding the Keyboard shortcuts help sheet and the system helper. */
val keyboardHelpEntries: List<KeyboardHelpEntry> = buildList {
    add(KeyboardInputAction.StartSearch)
    addAll(KeyboardShortcut.entries)
    add(KeyboardInputAction.Back)
    add(KeyboardInputAction.MoveRowFocus)
    add(KeyboardInputAction.OpenFocusedRow)
}

/** Resolves [entry]'s action label for the system helper and the Keyboard shortcuts help sheet. */
fun resolveKeyboardHelpEntryLabel(entry: KeyboardHelpEntry, resources: Resources): String =
    resources.getString(entry.label, *entry.labelArguments.toTypedArray())
