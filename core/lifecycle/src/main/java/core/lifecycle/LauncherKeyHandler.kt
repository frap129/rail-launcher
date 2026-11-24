package core.lifecycle

import android.view.KeyEvent
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.LauncherInputState
import core.ui.model.data.LauncherSignal
import core.ui.model.data.hasShortcutModifier

/**
 * Routes a hardware key press in the launcher activity: Esc acts as Back on every screen, while
 * the listed keyboard shortcuts open Search, the Agenda, Rail Settings or a row by index, and Tab
 * switches between Home and the App List when no Pop-up or Home edit mode owns input. Every
 * unlisted Ctrl, Alt or Meta combination and modified Tab passes through, and shortcuts are ignored
 * while a text field has focus so typing never triggers them. A Typed character opens Search or
 * appends to its query when the launcher accepts typing, and is consumed; otherwise the key passes
 * through.
 */
class LauncherKeyHandler(
    private val isLauncherShown: () -> Boolean,
    private val inputState: LauncherInputState,
    private val onBack: () -> Unit,
    private val onSignal: (LauncherSignal) -> Unit
) {
    /** Handles [event] and returns whether it was consumed. */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && !event.hasShortcutModifier()) {
            onBack()
            return true
        }
        val shortcut: KeyboardShortcut? = KeyboardShortcut.forEvent(keyCode = keyCode, event = event)
        if (shortcut != null) {
            if (!isLauncherShown() || inputState.isTextFieldFocused.value) {
                return false
            }
            if (shortcut.signal == LauncherSignal.ToggleAppList && !inputState.canToggleAppList.value) {
                return false
            }
            onSignal(shortcut.signal)
            return true
        }
        if (!isLauncherShown() || !inputState.acceptsTyping.value) {
            return false
        }
        val character: String = event.typedCharacter() ?: return false
        onSignal(LauncherSignal.TypedCharacter(character = character))
        return true
    }
}
