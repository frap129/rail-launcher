package core.data.testing

import core.data.room.dao.MediaPlayerSettingsDao
import core.data.widgets.MediaPlayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeMediaPlayerSettingsDao(initialSettings: MediaPlayerSettings = MediaPlayerSettings.Default) : MediaPlayerSettingsDao() {
    private val stored: MutableStateFlow<MediaPlayerSettings?> = MutableStateFlow(initialSettings)

    override fun observeRow(): Flow<MediaPlayerSettings?> = stored

    override suspend fun getRow(): MediaPlayerSettings? = stored.value

    override suspend fun upsert(settings: MediaPlayerSettings) {
        stored.update { settings }
    }
}
