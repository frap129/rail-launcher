package core.ui.composables

import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import core.ui.R
import core.ui.model.data.KeyboardHelpEntry
import core.ui.model.data.KeyboardShortcutModifier

@Composable
fun formatKeyCombination(entry: KeyboardHelpEntry, keyCharacterMap: KeyCharacterMap): String {
    val keyLabel: String = entry.keyLabel?.let { keyLabelResource: Int ->
        stringResource(keyLabelResource)
    } ?: run {
        val keyLabels: List<String> = entry.keyCodes.map { keyCode: Int ->
            formatKeyboardKey(keyCode = keyCode, keyCharacterMap = keyCharacterMap)
        }
        keyLabels.reduce { first: String, second: String ->
            stringResource(R.string.keyboard_shortcut_key_variants, first, second)
        }
    }
    val modifierLabels: List<String> = entry.modifiers
        .map { modifier: KeyboardShortcutModifier -> formatKeyboardModifier(modifier) }
        .filter { label: String -> label.isNotEmpty() }
    if (modifierLabels.isEmpty()) {
        return keyLabel
    }
    val alternativesLabel: String = modifierLabels.reduce { first: String, second: String ->
        stringResource(R.string.keyboard_shortcut_alternative, first, second)
    }
    val combinationLabel: String = stringResource(R.string.keyboard_shortcut_combination, alternativesLabel, keyLabel)
    return if (entry.isShiftAllowed) {
        stringResource(R.string.keyboard_shortcut_shift_optional, combinationLabel)
    } else {
        combinationLabel
    }
}

@Composable
private fun formatKeyboardModifier(modifier: KeyboardShortcutModifier): String = when (modifier) {
    KeyboardShortcutModifier.None -> ""
    KeyboardShortcutModifier.Control -> stringResource(R.string.keyboard_shortcut_ctrl)
    KeyboardShortcutModifier.Meta -> stringResource(R.string.keyboard_shortcut_meta)
}

@Composable
internal fun formatKeyboardKey(keyCode: Int, keyCharacterMap: KeyCharacterMap): String {
    if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
        val digit: Int = keyCode - KeyEvent.KEYCODE_NUMPAD_0
        return stringResource(R.string.keyboard_shortcut_numpad, digit)
    }
    if (keyCode == KeyEvent.KEYCODE_TAB) {
        return stringResource(R.string.keyboard_shortcut_tab)
    }
    val displayLabel: Char = keyCharacterMap.getDisplayLabel(keyCode)
    return if (displayLabel.isLetter()) displayLabel.uppercaseChar().toString() else displayLabel.toString()
}
