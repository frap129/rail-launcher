package core.ui.model.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the launcher screen currently accepts typed characters, updated while it is shown. */
class LauncherTypingState {
    private val mutableAcceptsTyping: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val acceptsTyping: StateFlow<Boolean> = mutableAcceptsTyping

    fun setAcceptsTyping(acceptsTyping: Boolean) {
        mutableAcceptsTyping.value = acceptsTyping
    }
}
