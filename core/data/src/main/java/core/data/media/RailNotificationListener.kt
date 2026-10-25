package core.data.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import core.data.notifications.INotificationListenerRelay
import core.data.notifications.toNotificationHandle
import core.data.notifications.toNotificationRecord
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Holds Rail's notification-listener binding. Access lets the Media Player list
 * active MediaSessions and lets Pop-ups show notifications through
 * [INotificationListenerRelay]; nothing is persisted or logged beyond keys.
 */
class RailNotificationListener :
    NotificationListenerService(),
    KoinComponent {
    private val relay: INotificationListenerRelay by inject()

    override fun onListenerConnected() {
        relay.attach(service = this)
        relay.refreshFromService()
        mutableConnections.tryEmit(true)
    }

    override fun onListenerDisconnected() {
        relay.detach(service = this)
        mutableConnections.tryEmit(false)
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        relay.post(
            record = statusBarNotification.toNotificationRecord(),
            handle = statusBarNotification.toNotificationHandle()
        )
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        relay.remove(key = statusBarNotification.key)
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
