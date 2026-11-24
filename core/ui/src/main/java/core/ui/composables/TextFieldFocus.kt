package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
 * Reports this text field's focus to [LocalLauncherInputState] while the field is shown, so no
 * launcher keyboard shortcut fires while any text field can be typed into.
 *
 * Each text field reports with a token of its own and drops it when it leaves the screen, so
 * focus moving between fields never clears the launcher's focus state.
 */
@Composable
fun Modifier.tracksTextFieldFocus(): Modifier {
    val inputState: LauncherInputState = LocalLauncherInputState.current
    val token: TextFieldFocusToken = remember { TextFieldFocusToken() }
    val currentInputState: LauncherInputState by rememberUpdatedState(inputState)
    DisposableEffect(token, currentInputState) {
        onDispose { currentInputState.setTextFieldFocused(token = token, isFocused = false) }
    }
    return onFocusChanged { focusState ->
        currentInputState.setTextFieldFocused(token = token, isFocused = focusState.isFocused)
    }
}
