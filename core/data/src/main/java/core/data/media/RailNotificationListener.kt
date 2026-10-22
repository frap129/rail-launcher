package core.data.media

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Holds Rail's notification-listener binding. It never reads notifications;
 * access only grants [android.media.session.MediaSessionManager] the ability
 * to list and control active MediaSessions.
 */
class RailNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        mutableConnections.tryEmit(true)
    }

    override fun onListenerDisconnected() {
        mutableConnections.tryEmit(false)
    }

    companion object {
        private val mutableConnections: MutableSharedFlow<Boolean> = MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        /** Emits true when the system connects this listener and false when it disconnects. */
        val connections: SharedFlow<Boolean> = mutableConnections.asSharedFlow()
    }
}
