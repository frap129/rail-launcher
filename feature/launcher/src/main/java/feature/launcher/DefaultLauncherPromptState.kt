package feature.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the default launcher prompt dismissal for the current process only. */
class DefaultLauncherPromptState {
    private val dismissed: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = dismissed.asStateFlow()

    fun dismiss() {
        dismissed.value = true
    }
}
