package core.data.testing

import core.data.media.MediaCommand
import core.data.media.MediaSessionKey
import core.data.media.MediaSessionSnapshot
import core.data.media.PlaybackStatus

/** The Media Player App label shared by Media Player tests. */
const val MEDIA_APP_LABEL: String = "Music"

/** A Media Player session with the defaults shared by Media Player tests. */
fun createMediaSessionSnapshot(
    key: String = "session",
    title: String = "Song",
    artist: String? = "Artist",
    packageName: String = "com.example.music",
    appLabel: String = MEDIA_APP_LABEL,
    state: PlaybackStatus = PlaybackStatus.PLAYING,
    actions: Set<MediaCommand> = setOf(MediaCommand.Play, MediaCommand.Pause)
): MediaSessionSnapshot = MediaSessionSnapshot(
    key = MediaSessionKey(value = key),
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    artist = artist,
    artwork = null,
    state = state,
    actions = actions
)
