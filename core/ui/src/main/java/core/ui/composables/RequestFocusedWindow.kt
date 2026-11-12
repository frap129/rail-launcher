package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Runs [onFocused] once while Rail is resumed and the host window has focus, and once more after
 * each later resume, but never for a window focus change alone. A request made before the window
 * regains focus, such as when a modal sheet closes, waits for that focus.
 */
@Composable
fun RequestFocusedWindow(onFocused: () -> Unit) {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val windowInfo: WindowInfo = LocalWindowInfo.current
    fun isLifecycleResumed(): Boolean = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    var isResumed: Boolean by remember(lifecycleOwner) { mutableStateOf(isLifecycleResumed()) }
    var hasPendingRequest: Boolean by remember(lifecycleOwner) { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
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
    val currentOnFocused: () -> Unit by rememberUpdatedState(onFocused)
    LaunchedEffect(windowInfo.isWindowFocused, isResumed, hasPendingRequest) {
        if (windowInfo.isWindowFocused && isResumed && hasPendingRequest) {
            hasPendingRequest = false
            currentOnFocused()
        }
    }
}
