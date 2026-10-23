package core.data.media

import core.data.widgets.MediaPlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlayerSelectorTest {
    @Test
    fun `returns null while the Media Player is disabled`() {
        val inputSession: MediaSessionSnapshot = session(title = "Song", state = PlaybackStatus.PLAYING)

        assertNull(MediaPlayerSelector.selectSession(sessions = listOf(inputSession), settings = MediaPlayerSettings(enabled = false)))
    }

    @Test
    fun `picks the first eligible session in system order`() {
        val inputFirst: MediaSessionSnapshot = session(key = "first", title = "First", state = PlaybackStatus.PAUSED)
        val inputSecond: MediaSessionSnapshot = session(key = "second", title = "Second", state = PlaybackStatus.PLAYING)

        val actualSelected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(inputFirst, inputSecond),
            settings = MediaPlayerSettings(enabled = true)
        )

        assertEquals(inputFirst, actualSelected)
    }

    @Test
    fun `skips blank, stopped, none and error sessions`() {
        val inputSessions: List<MediaSessionSnapshot> = listOf(
            session(key = "blank", title = " ", state = PlaybackStatus.PLAYING),
            session(key = "stopped", title = "Stopped", state = PlaybackStatus.STOPPED),
            session(key = "none", title = "None", state = PlaybackStatus.NONE),
            session(key = "error", title = "Error", state = PlaybackStatus.ERROR),
            session(key = "buffering", title = "Buffering", state = PlaybackStatus.BUFFERING)
        )

        val actualSelected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = inputSessions,
            settings = MediaPlayerSettings(enabled = true)
        )

        assertEquals("buffering", actualSelected?.key?.value)
    }

    @Test
    fun `returns null when no session is eligible`() {
        val inputSessions: List<MediaSessionSnapshot> = listOf(
            session(title = "", state = PlaybackStatus.PLAYING),
            session(title = "Stopped", state = PlaybackStatus.STOPPED)
        )

        assertNull(MediaPlayerSelector.selectSession(sessions = inputSessions, settings = MediaPlayerSettings(enabled = true)))
    }

    @Test
    fun `skips excluded packages`() {
        val inputExcluded: MediaSessionSnapshot = session(
            key = "excluded",
            packageName = "com.example.video",
            appLabel = "Video",
            state = PlaybackStatus.PLAYING
        )
        val inputAllowed: MediaSessionSnapshot = session(key = "allowed", state = PlaybackStatus.PLAYING)
        val actualSelected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(inputExcluded, inputAllowed),
            settings = MediaPlayerSettings(enabled = true, excludedPackages = setOf("com.example.video"))
        )
        assertEquals(inputAllowed, actualSelected)
    }

    @Test
    fun `shows a paused session when show when paused is on`() {
        val inputPaused: MediaSessionSnapshot = session(state = PlaybackStatus.PAUSED)
        val actualSelected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(inputPaused),
            settings = MediaPlayerSettings(enabled = true, showWhenPaused = true)
        )
        assertEquals(inputPaused, actualSelected)
    }

    @Test
    fun `skips a paused session when show when paused is off`() {
        val inputPaused: MediaSessionSnapshot = session(state = PlaybackStatus.PAUSED)
        assertNull(
            MediaPlayerSelector.selectSession(
                sessions = listOf(inputPaused),
                settings = MediaPlayerSettings(enabled = true, showWhenPaused = false)
            )
        )
    }

    @Test
    fun `lets a lower playing session win over a higher paused session when show when paused is off`() {
        val inputPaused: MediaSessionSnapshot = session(key = "paused", state = PlaybackStatus.PAUSED)
        val inputPlaying: MediaSessionSnapshot = session(key = "playing", state = PlaybackStatus.PLAYING)
        val actualSelected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(inputPaused, inputPlaying),
            settings = MediaPlayerSettings(enabled = true, showWhenPaused = false)
        )
        assertEquals(inputPlaying, actualSelected)
    }

    private fun session(
        key: String = "session",
        title: String = "Song",
        packageName: String = "com.example.music",
        appLabel: String = "Music",
        state: PlaybackStatus = PlaybackStatus.PLAYING
    ): MediaSessionSnapshot = MediaSessionSnapshot(
        key = MediaSessionKey(value = key),
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        artist = "Artist",
        artwork = null,
        state = state,
        actions = setOf(MediaCommand.Play, MediaCommand.Pause)
    )
}
