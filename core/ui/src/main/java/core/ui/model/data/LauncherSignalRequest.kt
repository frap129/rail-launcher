package core.ui.model.data

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

/** The ordered launcher signals waiting for the launcher screen. */
private const val LAUNCHER_SIGNALS_KEY: String = "launcherSignals"

/**
 * A queue of launcher signals for the launcher screen, saved with the destination so signals
 * requested before the launcher collects them are all delivered in order, including across a
 * recreation. Collected signals are removed before they are delivered, keeping each one one-shot.
 */
class LauncherSignalRequest(private val savedStateHandle: SavedStateHandle) {
    private val pendingSignals: StateFlow<List<LauncherSignal>> = savedStateHandle.getStateFlow(
        key = LAUNCHER_SIGNALS_KEY,
        initialValue = emptyList<LauncherSignal>()
    )

    fun request(signal: LauncherSignal) {
        savedStateHandle[LAUNCHER_SIGNALS_KEY] = ArrayList(pendingSignals.value).apply { add(signal) }
    }

    suspend fun collectRequests(onRequest: (LauncherSignal) -> Unit) {
        pendingSignals.collect { signals ->
            if (signals.isEmpty()) {
                return@collect
            }
            savedStateHandle[LAUNCHER_SIGNALS_KEY] = ArrayList<LauncherSignal>()
            signals.forEach(onRequest)
        }
    }
}
