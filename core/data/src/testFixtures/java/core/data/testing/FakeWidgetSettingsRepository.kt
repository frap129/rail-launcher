package core.data.testing

import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.WidgetAreaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetSettingsRepository(
    initialSettings: HeaderSettings = HeaderSettings(),
    initialEventSettings: EventSettings = EventSettings(),
    initialAreaSettings: WidgetAreaSettings = WidgetAreaSettings.Default
) : IWidgetSettingsRepository {
    private val storedSettings: MutableStateFlow<HeaderSettings> = MutableStateFlow(initialSettings)
    private val storedEventSettings: MutableStateFlow<EventSettings> = MutableStateFlow(initialEventSettings)
    private val storedAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(initialAreaSettings)

    val updates: MutableList<HeaderSettings> = mutableListOf()
    val eventUpdates: MutableList<EventSettings> = mutableListOf()
    val areaUpdates: MutableList<WidgetAreaSettings> = mutableListOf()

    override val headerSettings: Flow<HeaderSettings> = storedSettings

    override val eventSettings: Flow<EventSettings> = storedEventSettings

    override val widgetAreaSettings: Flow<WidgetAreaSettings> = storedAreaSettings

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

    override suspend fun updateWidgetAreaSettings(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
        val updated: WidgetAreaSettings = transform(storedAreaSettings.value)
        storedAreaSettings.update { updated }
        areaUpdates.add(updated)
    }
}
