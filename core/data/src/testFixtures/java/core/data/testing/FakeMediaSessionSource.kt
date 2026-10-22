package core.data.testing

import core.data.media.IMediaSessionSource
import core.data.media.MediaCommand
import core.data.media.MediaSessionKey
import core.data.media.MediaSessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMediaSessionSource(initialSessions: List<MediaSessionSnapshot> = emptyList(), hasAccess: Boolean = true) : IMediaSessionSource {
    private val access: MutableStateFlow<Boolean> = MutableStateFlow(hasAccess)
    private val storedSessions: MutableStateFlow<List<MediaSessionSnapshot>> = MutableStateFlow(initialSessions)

    override val hasAccess: StateFlow<Boolean> = access.asStateFlow()

    override val sessions: Flow<List<MediaSessionSnapshot>> = storedSessions

    val commands: MutableList<Pair<MediaSessionKey, MediaCommand>> = mutableListOf()
    val opened: MutableList<MediaSessionKey> = mutableListOf()
    var openResult: Boolean = true
    var refreshCount: Int = 0
        private set

    fun setSessions(sessions: List<MediaSessionSnapshot>) {
        storedSessions.value = sessions
    }

    fun setAccess(granted: Boolean) {
        access.value = granted
    }

    override fun refreshAccess() {
        refreshCount++
    }

    override fun execute(key: MediaSessionKey, command: MediaCommand) {
        commands.add(key to command)
    }

    override fun open(key: MediaSessionKey): Boolean {
        opened.add(key)
        return openResult
    }
}
