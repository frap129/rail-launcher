package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.WindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Requests focus for [focusRequester] and shows the keyboard when its content attaches, again on
 * every resume while the content stays composed, and when the window regains focus after Rail
 * returns from the background.
 */
@Composable
fun RequestKeyboardFocus(focusRequester: FocusRequester) {
    val keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val windowInfo: WindowInfo = LocalWindowInfo.current
    fun requestFocusAndShowKeyboard() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(focusRequester) {
        requestFocusAndShowKeyboard()
    }
    LaunchedEffect(windowInfo.isWindowFocused, focusRequester) {
        if (windowInfo.isWindowFocused) {
            requestFocusAndShowKeyboard()
        }
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
