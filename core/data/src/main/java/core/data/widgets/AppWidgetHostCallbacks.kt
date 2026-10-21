package core.data.widgets

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Relays [android.appwidget.AppWidgetHost] callbacks that Rail's host subclass receives. */
class AppWidgetHostCallbacks {
    private val changes: MutableSharedFlow<Unit> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val providerChanges: Flow<Unit> = changes.asSharedFlow()

    fun onProvidersChanged() {
        changes.tryEmit(Unit)
    }
}
