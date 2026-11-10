package core.lifecycle

import android.view.KeyEvent
import core.ui.model.data.LauncherSignal

/**
 * Routes a hardware key press in the launcher activity: Esc acts as Back on every screen, while
 * Ctrl, Alt and Meta shortcuts pass through. A Typed character opens Search or appends to its
 * query when the launcher accepts typing, and is consumed; otherwise the key passes through.
 */
class LauncherKeyHandler(
    private val isLauncherShown: () -> Boolean,
    private val isTypingAccepted: () -> Boolean,
    private val onBack: () -> Unit,
    private val onSignal: (LauncherSignal) -> Unit
) {
    /** Handles [event] and returns whether it was consumed. */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.hasShortcutModifier()) {
            return false
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBack()
            return true
        }
        if (!isLauncherShown() || !isTypingAccepted()) {
            return false
        }
        val character: String = event.typedCharacter() ?: return false
        onSignal(LauncherSignal.TypedCharacter(character = character))
        return true
    }
}
