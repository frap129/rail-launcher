package core.lifecycle

import android.content.Intent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import core.ui.model.data.SEARCH_ENTRY_URI

/**
 * Whether this intent is the Search entry other apps call to open Search,
 * `rail-launcher://search`. Only the exact ACTION_VIEW intent counts, so no other intent opens Search.
 */
fun Intent.isSearchEntry(): Boolean = action == Intent.ACTION_VIEW && data?.toString() == SEARCH_ENTRY_URI

/**
 * The printable character this hardware key press types, or null when it is a shortcut (Ctrl, Alt
 * or Meta), a soft or virtual keyboard key, a dead key, whitespace, or a key that types nothing.
 */
fun KeyEvent.typedCharacter(): String? {
    if (hasShortcutModifier()) {
        return null
    }
    return printableHardwareCharacter(codePoint = unicodeChar, deviceId = deviceId, flags = flags)
}

internal fun KeyEvent.hasShortcutModifier(): Boolean = isCtrlPressed || isAltPressed || isMetaPressed

internal fun printableHardwareCharacter(codePoint: Int, deviceId: Int, flags: Int): String? {
    if (deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) {
        return null
    }
    if (flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) {
        return null
    }
    if (codePoint == 0) {
        return null
    }
    if ((codePoint and KeyCharacterMap.COMBINING_ACCENT) != 0) {
        return null
    }
    if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
        return null
    }
    return String(Character.toChars(codePoint))
}
