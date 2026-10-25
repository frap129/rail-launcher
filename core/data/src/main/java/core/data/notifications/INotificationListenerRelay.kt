package core.data.notifications

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.StateFlow

/** Keeps the notifications reported by the listener and the actions Rail can run on them. */
interface INotificationListenerRelay {
    /** The current records. */
    val records: StateFlow<List<NotificationRecord>>

    /** Starts or stops keeping reported notifications. */
    fun setCollecting(collecting: Boolean)

    fun attach(service: NotificationListenerService)

    fun detach(service: NotificationListenerService)

    fun post(record: NotificationRecord, handle: NotificationHandle)

    fun remove(key: String)

    fun record(key: String): NotificationRecord?

    fun handle(key: String): NotificationHandle?

    /** Asks the system to cancel [key]. */
    fun cancel(key: String)

    /** Rebuilds the records from the notifications the system still reports. */
    fun refreshFromService()

    fun clear()
}
