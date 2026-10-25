package core.data.notifications

import android.app.PendingIntent
import android.content.Context
import core.data.media.RailNotificationListener
import core.data.popups.IPopupRepository
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** In-memory notification source backed by the listener Rail keeps connected. */
class INotificationSourceImpl(
    context: Context,
    scope: CoroutineScope,
    private val popupRepository: IPopupRepository,
    private val relay: INotificationListenerRelay,
    private val isAccessGranted: (Context) -> Boolean = ::isNotificationAccessGranted
) : INotificationSource {
    private val appContext: Context = context.applicationContext
    private val access: MutableStateFlow<Boolean> = MutableStateFlow(isAccessGranted(appContext))
    private val observers: AtomicInteger = AtomicInteger(0)

    override val hasAccess: StateFlow<Boolean> = access.asStateFlow()

    private val notificationStream: SharedFlow<List<NotificationSnapshot>> = callbackFlow {
        val settingsJob: Job = launch {
            combine(popupRepository.notificationsEnabled, access) { enabled, granted -> enabled && granted }
                .collect { isActive -> applyCollecting(isActive = isActive) }
        }
        val recordsJob: Job = launch {
            combine(popupRepository.notificationsEnabled, access, relay.records) { enabled, granted, records ->
                if (!enabled || !granted) {
                    emptyList()
                } else {
                    NotificationTextMapper.snapshots(records = records, ownPackageName = appContext.packageName)
                }
            }.collect { snapshots -> trySend(snapshots) }
        }
        awaitClose {
            settingsJob.cancel()
            recordsJob.cancel()
            relay.setCollecting(collecting = false)
        }
    }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            replay = 1
        )

    override val notifications: Flow<List<NotificationSnapshot>> = notificationStream
        .onStart {
            observers.incrementAndGet()
            val enabled: Boolean = popupRepository.notificationsEnabled.first()
            applyCollecting(isActive = enabled && access.value)
        }
        .onCompletion {
            if (observers.decrementAndGet() == 0) {
                relay.setCollecting(collecting = false)
            }
        }

    init {
        scope.launch {
            RailNotificationListener.connections.collect { connected ->
                if (connected) {
                    access.value = true
                } else {
                    refreshAccess()
                }
            }
        }
    }

    private fun applyCollecting(isActive: Boolean) {
        relay.setCollecting(collecting = isActive)
        if (isActive) {
            relay.refreshFromService()
        }
    }

    override fun refreshAccess() {
        access.value = isAccessGranted(appContext)
    }

    override suspend fun open(key: String) {
        val handle: NotificationHandle = relay.handle(key) ?: return
        try {
            handle.contentIntent?.send()
        } catch (exception: PendingIntent.CanceledException) {
            Timber.w(exception, "A notification's content intent was cancelled")
        }
        if (handle.isAutoCancel) {
            relay.remove(key)
            relay.cancel(key)
        }
    }

    override suspend fun dismiss(key: String) {
        val record: NotificationRecord = relay.record(key) ?: return
        if (!record.isClearable) {
            return
        }
        relay.remove(key)
        relay.cancel(key)
    }
}
