package core.lifecycle

import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import core.ui.model.data.KeyboardHelpEntry
import core.ui.model.data.keyboardHelpEntries

/**
 * The system keyboard shortcuts helper group listing every [KeyboardHelpEntry.keyCodes] variant for
 * each accepted [KeyboardHelpEntry.modifierStates] from the single list shared with the help sheet.
 *
 * @param label the group's label in the helper
 * @param resolveLabel names each help entry's action in the helper
 */
fun createKeyboardShortcutGroup(label: CharSequence, resolveLabel: (KeyboardHelpEntry) -> CharSequence): KeyboardShortcutGroup {
    val shortcutInfos: List<KeyboardShortcutInfo> = keyboardHelpEntries.flatMap { entry: KeyboardHelpEntry ->
        entry.keyCodes.flatMap { keyCode: Int ->
            entry.modifierStates.map { modifiers: Int ->
                KeyboardShortcutInfo(resolveLabel(entry), keyCode, modifiers)
            }
        }
    }
    return KeyboardShortcutGroup(label, shortcutInfos)
}
