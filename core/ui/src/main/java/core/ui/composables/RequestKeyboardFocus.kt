package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Requests focus for [focusRequester] and shows the keyboard once when its content attaches
 * while Rail is resumed, and once more on each later resume. Each request waits for the window
 * focus the IME needs, so a window that merely regains focus, such as when the notification
 * shade closes, never brings back a keyboard the user hid.
 */
@Composable
fun RequestKeyboardFocus(focusRequester: FocusRequester) {
    val keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val windowInfo: WindowInfo = LocalWindowInfo.current
    fun isLifecycleResumed(): Boolean = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    var isResumed: Boolean by remember(focusRequester) { mutableStateOf(isLifecycleResumed()) }
    var hasPendingRequest: Boolean by remember(focusRequester) { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner, focusRequester) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!isResumed) {
                    isResumed = true
                    hasPendingRequest = true
                }

                Lifecycle.Event.ON_PAUSE -> isResumed = false

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(windowInfo.isWindowFocused, isResumed, focusRequester, hasPendingRequest) {
        if (windowInfo.isWindowFocused && isResumed && hasPendingRequest) {
            hasPendingRequest = false
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
