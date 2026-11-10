package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Requests focus for [focusRequester] and shows the keyboard when its content attaches, and again
 * on every resume while the content stays composed.
 */
@Composable
fun RequestKeyboardFocus(focusRequester: FocusRequester) {
    val keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    fun requestFocusAndShowKeyboard() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(focusRequester) {
        requestFocusAndShowKeyboard()
    }
    DisposableEffect(lifecycleOwner, focusRequester) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                requestFocusAndShowKeyboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
