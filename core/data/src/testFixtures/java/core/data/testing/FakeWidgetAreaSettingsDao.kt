package core.data.testing

import core.data.room.dao.WidgetAreaSettingsDao
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAreaSettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetAreaSettingsDao(initialSettings: WidgetAreaSettings = WidgetAreaSettings.Default) : WidgetAreaSettingsDao() {
    private val stored: MutableStateFlow<WidgetAreaSettingsRecord?> =
        MutableStateFlow(WidgetAreaSettingsRecord.from(initialSettings))

    override fun observeRow(): Flow<WidgetAreaSettingsRecord?> = stored

    override suspend fun getRow(): WidgetAreaSettingsRecord? = stored.value

    override suspend fun upsert(settings: WidgetAreaSettingsRecord) {
        stored.update { settings }
    }
}
