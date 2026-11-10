package core.ui.model.data

import androidx.lifecycle.SavedStateHandle

/** Whether the launcher was already resumed when the requested HOME intent arrived. */
private const val HOME_INTENT_WAS_RESUMED_KEY: String = "homeIntentWasResumed"

/**
 * A one-shot request to handle a HOME intent, carrying whether the launcher was already resumed
 * when it arrived. Only a press that arrives while Rail is resumed and showing Home opens Search.
 */
class HomeIntentRequest(private val savedStateHandle: SavedStateHandle) {
    private val request: OneShotRequest = OneShotRequest(
        savedStateHandle = savedStateHandle,
        key = HOME_INTENT_REQUEST_KEY
    )

    fun request(wasResumed: Boolean) {
        savedStateHandle[HOME_INTENT_WAS_RESUMED_KEY] = wasResumed
        request.request()
    }

    suspend fun collectRequests(onRequest: (wasResumed: Boolean) -> Unit) {
        request.collectRequests {
            onRequest(savedStateHandle[HOME_INTENT_WAS_RESUMED_KEY] ?: false)
        }
    }
}
