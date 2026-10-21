package core.data.widgets

import core.data.prefs.HeaderSettings
import kotlinx.coroutines.flow.Flow

interface IWidgetSettingsRepository {
    val headerSettings: Flow<HeaderSettings>

    suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings)
}
