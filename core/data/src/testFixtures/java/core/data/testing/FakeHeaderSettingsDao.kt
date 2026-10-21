package core.data.testing

import core.data.prefs.HeaderSettings
import core.data.room.dao.HeaderSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHeaderSettingsDao(initialSettings: HeaderSettings? = null) : HeaderSettingsDao() {
    private val storedSettings: MutableStateFlow<HeaderSettings?> = MutableStateFlow(initialSettings)
    var upsertCount: Int = 0
        private set

    override fun observeRow(): Flow<HeaderSettings?> = storedSettings

    override suspend fun getRow(): HeaderSettings? = storedSettings.value

    override suspend fun upsert(settings: HeaderSettings) {
        storedSettings.value = settings
        upsertCount++
    }
}
