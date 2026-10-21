package core.data.testing

import core.data.prefs.HeaderSettings
import core.data.widgets.IWidgetSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetSettingsRepository(initialSettings: HeaderSettings = HeaderSettings()) : IWidgetSettingsRepository {
    private val storedSettings: MutableStateFlow<HeaderSettings> = MutableStateFlow(initialSettings)

    val updates: MutableList<HeaderSettings> = mutableListOf()

    override val headerSettings: Flow<HeaderSettings> = storedSettings

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        val updated: HeaderSettings = transform(storedSettings.value)
        storedSettings.update { updated }
        updates.add(updated)
    }
}
