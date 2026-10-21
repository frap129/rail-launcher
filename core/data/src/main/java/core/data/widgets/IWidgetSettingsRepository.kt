package core.data.widgets

import core.data.prefs.BatteryVisibility
import core.data.prefs.HeaderSettings
import core.data.prefs.withBatteryVisibility
import core.data.prefs.withClockVisible
import core.data.prefs.withDateVisible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface IWidgetSettingsRepository {
    val headerSettings: Flow<HeaderSettings>

    suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings)

    suspend fun setClockVisible(visible: Boolean) {
        updateIfChanged { settings -> settings.withClockVisible(visible) }
    }

    suspend fun setDateVisible(visible: Boolean) {
        updateIfChanged { settings -> settings.withDateVisible(visible) }
    }

    suspend fun setBatteryVisibility(visibility: BatteryVisibility) {
        updateIfChanged { settings -> settings.withBatteryVisibility(visibility) }
    }
}

private suspend fun IWidgetSettingsRepository.updateIfChanged(transform: (HeaderSettings) -> HeaderSettings) {
    val current: HeaderSettings = headerSettings.first()
    if (transform(current) != current) {
        updateHeaderSettings(transform)
    }
}
