package core.data.widgets

import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.room.dao.EventSettingsDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.MediaPlayerSettingsDao
import core.data.room.dao.WidgetAreaSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class IWidgetSettingsRepositoryImpl(
    private val headerSettingsDao: HeaderSettingsDao,
    private val eventSettingsDao: EventSettingsDao,
    private val widgetAreaSettingsDao: WidgetAreaSettingsDao,
    private val mediaPlayerSettingsDao: MediaPlayerSettingsDao
) : IWidgetSettingsRepository {
    override val headerSettings: Flow<HeaderSettings> = headerSettingsDao.observe().distinctUntilChanged()

    override val eventSettings: Flow<EventSettings> = eventSettingsDao.observe().distinctUntilChanged()

    override val widgetAreaSettings: Flow<WidgetAreaSettings> = widgetAreaSettingsDao.observe().distinctUntilChanged()

    override val mediaPlayerSettings: Flow<MediaPlayerSettings> = mediaPlayerSettingsDao.observe().distinctUntilChanged()

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        headerSettingsDao.update(transform)
    }

    override suspend fun updateEventSettings(transform: (EventSettings) -> EventSettings) {
        eventSettingsDao.update(transform)
    }

    override suspend fun updateWidgetAreaSettings(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
        widgetAreaSettingsDao.update(transform)
    }

    override suspend fun updateMediaPlayerSettings(transform: (MediaPlayerSettings) -> MediaPlayerSettings) {
        mediaPlayerSettingsDao.update(transform)
    }
}
