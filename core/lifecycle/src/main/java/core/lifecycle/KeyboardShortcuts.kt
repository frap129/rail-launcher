package core.lifecycle

import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import core.ui.model.data.KeyboardShortcut

/**
 * The system keyboard shortcuts helper group listing every [KeyboardShortcut] for each of its
 * accepted [KeyboardShortcut.modifiers], so the helper and the key handler read one list.
 *
 * @param label the group's label in the helper
 * @param resolveLabel names each shortcut's action in the helper
 */
fun createKeyboardShortcutGroup(label: CharSequence, resolveLabel: (KeyboardShortcut) -> CharSequence): KeyboardShortcutGroup {
    val shortcutInfos: List<KeyboardShortcutInfo> = KeyboardShortcut.entries.flatMap { shortcut ->
        KeyboardShortcut.modifiers.map { modifiers ->
            KeyboardShortcutInfo(resolveLabel(shortcut), shortcut.keyCode, modifiers)
        }
    }
    return KeyboardShortcutGroup(label, shortcutInfos)
}
