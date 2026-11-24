package core.ui.model.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import core.ui.R

/**
 * A keyboard shortcut the launcher consumes: [keyCode] held with one of [modifiers], which the
 * launcher activity turns into [signal].
 *
 * [label] names the action in the system keyboard shortcuts helper and, later, the Keyboard
 * shortcuts help sheet, so a new shortcut is only added to this list.
 */
enum class KeyboardShortcut(@StringRes val label: Int, val keyCode: Int, val signal: LauncherSignal) {
    Search(label = R.string.search, keyCode = KeyEvent.KEYCODE_F, signal = LauncherSignal.OpenSearch),
    Agenda(label = R.string.agenda, keyCode = KeyEvent.KEYCODE_A, signal = LauncherSignal.OpenAgenda),
    Settings(label = R.string.settings, keyCode = KeyEvent.KEYCODE_S, signal = LauncherSignal.OpenSettings);

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
            return entries.firstOrNull { shortcut -> shortcut.keyCode == keyCode }
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
