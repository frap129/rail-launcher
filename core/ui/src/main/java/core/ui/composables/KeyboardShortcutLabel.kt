package core.ui.composables

import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import core.ui.R
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.NO_KEYBOARD_MODIFIERS
import java.util.Locale

private const val KEY_CODE_PREFIX: String = "KEYCODE_"

@Composable
fun formatKeyCombination(shortcut: KeyboardShortcut, keyCharacterMap: KeyCharacterMap): String {
    val keyLabels: List<String> = shortcut.keyCodes.map { keyCode: Int ->
        formatKeyboardKey(keyCode = keyCode, keyCharacterMap = keyCharacterMap)
    }
    val keyLabel: String = keyLabels.reduce { first: String, second: String ->
        stringResource(R.string.keyboard_shortcut_key_variants, first, second)
    }
    val modifierLabels: List<String> = shortcut.modifiers
        .map { modifier: Int -> formatKeyboardModifier(modifier) }
        .filter { label: String -> label.isNotEmpty() }
    if (modifierLabels.isEmpty()) {
        return keyLabel
    }
    val alternativesLabel: String = modifierLabels.reduce { first: String, second: String ->
        stringResource(R.string.keyboard_shortcut_alternative, first, second)
    }
    val combinationLabel: String = stringResource(R.string.keyboard_shortcut_combination, alternativesLabel, keyLabel)
    return if (shortcut.isShiftAllowed) {
        stringResource(R.string.keyboard_shortcut_shift_optional, combinationLabel)
    } else {
        combinationLabel
    }
}

@Composable
private fun formatKeyboardModifier(modifiers: Int): String = when (modifiers) {
    NO_KEYBOARD_MODIFIERS -> ""
    KeyEvent.META_CTRL_ON -> stringResource(R.string.keyboard_shortcut_ctrl)
    KeyEvent.META_META_ON -> stringResource(R.string.keyboard_shortcut_meta)
    else -> modifiers.toString()
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
    if (isPrintingKeyCode(keyCode)) {
        val displayLabel: Char = keyCharacterMap.getDisplayLabel(keyCode)
        if (displayLabel != Char.MIN_VALUE && !displayLabel.isWhitespace()) {
            return if (displayLabel.isLetter()) displayLabel.uppercaseChar().toString() else displayLabel.toString()
        }
    }
    val keyCodeName: String = KeyEvent.keyCodeToString(keyCode).removePrefix(KEY_CODE_PREFIX)
    return keyCodeName
        .lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { character: Char -> character.titlecase(Locale.ROOT) }
}

private fun isPrintingKeyCode(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
    keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
    keyCode in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH
