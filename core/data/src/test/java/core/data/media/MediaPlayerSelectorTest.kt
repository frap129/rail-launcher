package core.data.media

import core.data.widgets.MediaPlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlayerSelectorTest {
    @Test
    fun `returns null while the Media Player is disabled`() {
        val session: MediaSessionSnapshot = session(title = "Song", state = PlaybackStatus.PLAYING)

        assertNull(MediaPlayerSelector.selectSession(sessions = listOf(session), settings = MediaPlayerSettings(enabled = false)))
    }

    @Test
    fun `picks the first eligible session in system order`() {
        val first: MediaSessionSnapshot = session(key = "first", title = "First", state = PlaybackStatus.PAUSED)
        val second: MediaSessionSnapshot = session(key = "second", title = "Second", state = PlaybackStatus.PLAYING)

        val selected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(first, second),
            settings = MediaPlayerSettings(enabled = true)
        )

        assertEquals(first, selected)
    }

    @Test
    fun `skips blank, stopped, none and error sessions`() {
        val sessions: List<MediaSessionSnapshot> = listOf(
            session(key = "blank", title = " ", state = PlaybackStatus.PLAYING),
            session(key = "stopped", title = "Stopped", state = PlaybackStatus.STOPPED),
            session(key = "none", title = "None", state = PlaybackStatus.NONE),
            session(key = "error", title = "Error", state = PlaybackStatus.ERROR),
            session(key = "buffering", title = "Buffering", state = PlaybackStatus.BUFFERING)
        )

        val selected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = sessions,
            settings = MediaPlayerSettings(enabled = true)
        )

        assertEquals("buffering", selected?.key?.value)
    }

    @Test
    fun `returns null when no session is eligible`() {
        val sessions: List<MediaSessionSnapshot> = listOf(
            session(title = "", state = PlaybackStatus.PLAYING),
            session(title = "Stopped", state = PlaybackStatus.STOPPED)
        )

        assertNull(MediaPlayerSelector.selectSession(sessions = sessions, settings = MediaPlayerSettings(enabled = true)))
    }

    @Test
    fun `skips excluded packages`() {
        val excluded: MediaSessionSnapshot = session(
            key = "excluded",
            packageName = "com.example.video",
            appLabel = "Video",
            state = PlaybackStatus.PLAYING
        )
        val allowed: MediaSessionSnapshot = session(key = "allowed", state = PlaybackStatus.PLAYING)
        val selected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(excluded, allowed),
            settings = MediaPlayerSettings(enabled = true, excludedPackages = setOf("com.example.video"))
        )
        assertEquals(allowed, selected)
    }

    @Test
    fun `shows a paused session when show when paused is on`() {
        val paused: MediaSessionSnapshot = session(state = PlaybackStatus.PAUSED)
        val selected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(paused),
            settings = MediaPlayerSettings(enabled = true, showWhenPaused = true)
        )
        assertEquals(paused, selected)
    }

    @Test
    fun `skips a paused session when show when paused is off`() {
        val paused: MediaSessionSnapshot = session(state = PlaybackStatus.PAUSED)
        assertNull(
            MediaPlayerSelector.selectSession(
                sessions = listOf(paused),
                settings = MediaPlayerSettings(enabled = true, showWhenPaused = false)
            )
        )
    }

    @Test
    fun `lets a lower playing session win over a higher paused session when show when paused is off`() {
        val paused: MediaSessionSnapshot = session(key = "paused", state = PlaybackStatus.PAUSED)
        val playing: MediaSessionSnapshot = session(key = "playing", state = PlaybackStatus.PLAYING)
        val selected: MediaSessionSnapshot? = MediaPlayerSelector.selectSession(
            sessions = listOf(paused, playing),
            settings = MediaPlayerSettings(enabled = true, showWhenPaused = false)
        )
        assertEquals(playing, selected)
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
