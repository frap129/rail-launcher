package core.data.testing

import core.data.prefs.EventSettings
import core.data.room.dao.EventSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEventSettingsDao(initialSettings: EventSettings? = null) : EventSettingsDao() {
    private val storedSettings: MutableStateFlow<EventSettings?> = MutableStateFlow(initialSettings)
    var upsertCount: Int = 0
        private set

    override fun observeRow(): Flow<EventSettings?> = storedSettings

    override suspend fun getRow(): EventSettings? = storedSettings.value

    override suspend fun upsert(settings: EventSettings) {
        storedSettings.value = settings
        upsertCount++
    }
}
