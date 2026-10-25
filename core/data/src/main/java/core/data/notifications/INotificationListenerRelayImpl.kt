package core.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** In-memory holder for what [RailNotificationListener][core.data.media.RailNotificationListener] reports. */
class INotificationListenerRelayImpl : INotificationListenerRelay {
    private val mutableRecords: MutableStateFlow<List<NotificationRecord>> = MutableStateFlow(emptyList())
    private val handles: MutableMap<String, NotificationHandle> = ConcurrentHashMap()

    @Volatile
    private var listener: NotificationListenerService? = null

    @Volatile
    private var isCollecting: Boolean = false

    override val records: StateFlow<List<NotificationRecord>> = mutableRecords.asStateFlow()

    override fun setCollecting(collecting: Boolean) {
        isCollecting = collecting
        if (!collecting) {
            clear()
        }
    }

    override fun attach(service: NotificationListenerService) {
        listener = service
    }

    override fun detach(service: NotificationListenerService) {
        if (listener === service) {
            listener = null
        }
    }

    override fun post(record: NotificationRecord, handle: NotificationHandle) {
        if (!isCollecting) {
            return
        }
        handles[record.key] = handle
        mutableRecords.update { records -> records.filterNot { existing -> existing.key == record.key } + record }
    }

    override fun remove(key: String) {
        handles.remove(key)
        mutableRecords.update { records -> records.filterNot { record -> record.key == key } }
    }

    override fun record(key: String): NotificationRecord? = mutableRecords.value.firstOrNull { record -> record.key == key }

    override fun handle(key: String): NotificationHandle? = handles[key]

    override fun cancel(key: String) {
        val service: NotificationListenerService = listener ?: return
        try {
            service.cancelNotification(key)
        } catch (exception: RuntimeException) {
            Timber.w(exception, "Could not cancel a notification")
        }
    }

    override fun refreshFromService() {
        val service: NotificationListenerService = listener ?: return
        val active: List<StatusBarNotification> = try {
            service.activeNotifications.orEmpty().toList()
        } catch (exception: RuntimeException) {
            Timber.w(exception, "Could not read active notifications")
            return
        }
        val activeKeys: Set<String> = active.mapTo(mutableSetOf()) { notification -> notification.key }
        mutableRecords.update { records -> records.filter { record -> record.key in activeKeys } }
        handles.keys.retainAll(activeKeys)
        active.forEach { notification ->
            post(record = notification.toNotificationRecord(), handle = notification.toNotificationHandle())
        }
    }

    override fun clear() {
        mutableRecords.value = emptyList()
        handles.clear()
    }
}
