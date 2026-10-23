package core.ui.model.data

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

/** A one-shot navigation request stored on a destination's saved state. */
class OneShotRequest(private val savedStateHandle: SavedStateHandle, private val key: String) {
    val isRequested: StateFlow<Boolean> = savedStateHandle.getStateFlow(key = key, initialValue = false)

    fun request() {
        savedStateHandle[key] = true
    }

    fun consume() {
        savedStateHandle[key] = false
    }

    suspend fun collectRequests(onRequest: () -> Unit) {
        isRequested.collect { requested ->
            if (requested) {
                consume()
                onRequest()
            }
        }
    }
}
