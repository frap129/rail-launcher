package core.data.media

import core.data.widgets.MediaPlayerSettings

/** Picks the session the Media Player shows: the system's first eligible session. */
object MediaPlayerSelector {
    fun selectSession(sessions: List<MediaSessionSnapshot>, settings: MediaPlayerSettings): MediaSessionSnapshot? {
        if (!settings.enabled) {
            return null
        }
        return sessions.firstOrNull { session ->
            session.packageName !in settings.excludedPackages && session.isEligible(showWhenPaused = settings.showWhenPaused)
        }
    }

    fun MediaSessionSnapshot.isEligible(showWhenPaused: Boolean): Boolean = title.isNotBlank() &&
        state.showsMediaPlayer() &&
        (showWhenPaused || state != PlaybackStatus.PAUSED)

    private fun PlaybackStatus.showsMediaPlayer(): Boolean = when (this) {
        PlaybackStatus.PLAYING,
        PlaybackStatus.PAUSED,
        PlaybackStatus.BUFFERING,
        PlaybackStatus.CONNECTING,
        PlaybackStatus.FAST_FORWARDING,
        PlaybackStatus.REWINDING,
        PlaybackStatus.SKIPPING_TO_NEXT,
        PlaybackStatus.SKIPPING_TO_PREVIOUS,
        PlaybackStatus.SKIPPING_TO_QUEUE_ITEM -> true

        PlaybackStatus.STOPPED,
        PlaybackStatus.NONE,
        PlaybackStatus.ERROR -> false
    }
}
