package core.data.testing

import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.WidgetAreaSettings
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow

/** The mutable settings and recorded updates behind a [createWidgetSettingsRepositoryMock] repository. */
class WidgetSettingsRepositoryState(
    headerSettings: HeaderSettings = HeaderSettings(),
    eventSettings: EventSettings = EventSettings(),
    widgetAreaSettings: WidgetAreaSettings = WidgetAreaSettings.Default,
    mediaPlayerSettings: MediaPlayerSettings = MediaPlayerSettings.Default
) : IWidgetSettingsRepository {
    override val headerSettings: MutableStateFlow<HeaderSettings> = MutableStateFlow(headerSettings)
    override val eventSettings: MutableStateFlow<EventSettings> = MutableStateFlow(eventSettings)
    override val widgetAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(widgetAreaSettings)
    override val mediaPlayerSettings: MutableStateFlow<MediaPlayerSettings> = MutableStateFlow(mediaPlayerSettings)

    val headerUpdates: MutableList<HeaderSettings> = mutableListOf()
    val eventUpdates: MutableList<EventSettings> = mutableListOf()
    val areaUpdates: MutableList<WidgetAreaSettings> = mutableListOf()
    val mediaPlayerUpdates: MutableList<MediaPlayerSettings> = mutableListOf()

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        val updated: HeaderSettings = transform(headerSettings.value)
        headerSettings.value = updated
        headerUpdates.add(updated)
    }

    override suspend fun updateEventSettings(transform: (EventSettings) -> EventSettings) {
        val updated: EventSettings = transform(eventSettings.value)
        eventSettings.value = updated
        eventUpdates.add(updated)
    }

    override suspend fun updateWidgetAreaSettings(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
        val updated: WidgetAreaSettings = transform(widgetAreaSettings.value)
        widgetAreaSettings.value = updated
        areaUpdates.add(updated)
    }

    override suspend fun updateMediaPlayerSettings(transform: (MediaPlayerSettings) -> MediaPlayerSettings) {
        val updated: MediaPlayerSettings = transform(mediaPlayerSettings.value)
        mediaPlayerSettings.value = updated
        mediaPlayerUpdates.add(updated)
    }
}

/** A spy on [state], so the production default setters run against it and tests can verify their calls. */
fun createWidgetSettingsRepositoryMock(state: WidgetSettingsRepositoryState = WidgetSettingsRepositoryState()): IWidgetSettingsRepository =
    spyk(state)
