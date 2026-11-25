package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import core.ui.model.data.LauncherInputState
import core.ui.model.data.TextFieldFocusToken

/**
 * The launcher's input state, provided at the app root. A fresh state stands in where no app root
 * provides one, so a text field shown on its own still has a state to report to.
 */
val LocalLauncherInputState: ProvidableCompositionLocal<LauncherInputState> = staticCompositionLocalOf {
    LauncherInputState()
}

/**
 * Reports this main-window text field's focus to [LocalLauncherInputState] while the field is shown,
 * so no launcher keyboard shortcut fires while it can be typed into. Dialogs and bottom sheets run
 * in their own windows and must not apply this modifier.
 *
 * Each text field reports with a token of its own and releases it from the input state it registered
 * with when it leaves the screen, so focus moving between fields never clears the launcher's focus state.
 */
@Composable
fun Modifier.tracksTextFieldFocus(): Modifier {
    val inputState: LauncherInputState = LocalLauncherInputState.current
    val token: TextFieldFocusToken = remember { TextFieldFocusToken() }
    DisposableEffect(inputState, token) {
        onDispose { inputState.setTextFieldFocused(token = token, isFocused = false) }
    }
    return onFocusChanged { focusState: FocusState ->
        inputState.setTextFieldFocused(token = token, isFocused = focusState.isFocused)
    }
}
