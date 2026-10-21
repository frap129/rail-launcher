package core.data.widgets

import core.data.prefs.HeaderSettings
import core.data.room.dao.HeaderSettingsDao
import kotlinx.coroutines.flow.Flow

class IWidgetSettingsRepositoryImpl(private val headerSettingsDao: HeaderSettingsDao) : IWidgetSettingsRepository {
    override val headerSettings: Flow<HeaderSettings> = headerSettingsDao.observe()

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        headerSettingsDao.update(transform)
    }
}
