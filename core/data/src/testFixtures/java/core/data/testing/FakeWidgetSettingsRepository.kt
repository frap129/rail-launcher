package core.data.testing

import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.widgets.IWidgetSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetSettingsRepository(
    initialSettings: HeaderSettings = HeaderSettings(),
    initialEventSettings: EventSettings = EventSettings()
) : IWidgetSettingsRepository {
    private val storedSettings: MutableStateFlow<HeaderSettings> = MutableStateFlow(initialSettings)
    private val storedEventSettings: MutableStateFlow<EventSettings> = MutableStateFlow(initialEventSettings)

    val updates: MutableList<HeaderSettings> = mutableListOf()
    val eventUpdates: MutableList<EventSettings> = mutableListOf()

    override val headerSettings: Flow<HeaderSettings> = storedSettings

    override val eventSettings: Flow<EventSettings> = storedEventSettings

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        val updated: HeaderSettings = transform(storedSettings.value)
        storedSettings.update { updated }
        updates.add(updated)
    }

    override suspend fun updateEventSettings(transform: (EventSettings) -> EventSettings) {
        val updated: EventSettings = transform(storedEventSettings.value).withValidMaxEvents()
        storedEventSettings.update { updated }
        eventUpdates.add(updated)
    }
}
