package core.data.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only data source that reads system notifications. Nothing is persisted and
 * content intents stay inside the implementation.
 */
interface INotificationSource {
    /** Whether Rail's notification listener is enabled in system settings. */
    val hasAccess: StateFlow<Boolean>

    /** The visible notifications, newest first; empty while access or the setting is missing. */
    val notifications: Flow<List<NotificationSnapshot>>

    fun refreshAccess()

    /** Sends [key]'s content intent and cancels it when its app asked for auto-cancel. */
    suspend fun open(key: String)

    /** Cancels the clearable notification [key]. */
    suspend fun dismiss(key: String)

    /**
     * Runs the action at [index] on [key], filling [replyText] into its RemoteInput
     * when the action accepts one. Returns false when the action is missing or failed.
     */
    suspend fun runAction(key: String, index: Int, replyText: String? = null): Boolean
}
