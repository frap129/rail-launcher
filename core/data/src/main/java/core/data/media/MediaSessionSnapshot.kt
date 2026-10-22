package core.data.media

/** An opaque reference to the source MediaSession of one active player. */
@JvmInline
value class MediaSessionKey(val value: String)

/** An opaque handle to artwork kept in [MediaArtworkStore]. */
@JvmInline
value class MediaArtwork(val key: String)

/** The playback state of an active MediaSession. */
enum class PlaybackStatus {
    PLAYING,
    PAUSED,
    BUFFERING,
    CONNECTING,
    FAST_FORWARDING,
    REWINDING,
    SKIPPING_TO_NEXT,
    SKIPPING_TO_PREVIOUS,
    SKIPPING_TO_QUEUE_ITEM,
    STOPPED,
    NONE,
    ERROR
}

/** A transport action both supported by a session and offered by the Media Player. */
enum class MediaCommand {
    Previous,
    Play,
    Pause,
    Next
}

/** What Rail shows for one active MediaSession. */
data class MediaSessionSnapshot(
    val key: MediaSessionKey,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String?,
    val artwork: MediaArtwork?,
    val state: PlaybackStatus,
    val actions: Set<MediaCommand>
)
