package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Requests focus for [focusRequester] and shows the keyboard once when its content attaches
 * while Rail is resumed, and once more on each later resume. Each request waits for the window
 * focus the IME needs, so a window that merely regains focus, such as when the notification
 * shade closes, never brings back a keyboard the user hid.
 */
@Composable
fun RequestKeyboardFocus(focusRequester: FocusRequester) {
    val keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    RequestFocusedWindow {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}
