package core.data.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only data source that reads system MediaSessions. Everything above it
 * works with [MediaSessionSnapshot] values and [MediaCommand]s.
 */
interface IMediaSessionSource {
    /** Whether Rail's notification listener is enabled in system settings. */
    val hasAccess: StateFlow<Boolean>

    /** Active sessions in the system's ordering, while notification access is granted. */
    val sessions: Flow<List<MediaSessionSnapshot>>

    fun refreshAccess()

    fun execute(key: MediaSessionKey, command: MediaCommand)

    /** Opens the player's own UI. Returns false when no activity could be started. */
    fun open(key: MediaSessionKey): Boolean
}
