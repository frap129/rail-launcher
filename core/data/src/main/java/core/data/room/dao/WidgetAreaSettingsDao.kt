package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAreaSettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class WidgetAreaSettingsDao {
    @Query("SELECT * FROM widget_area_settings WHERE id = 0")
    abstract fun observeRow(): Flow<WidgetAreaSettingsRecord?>

    fun observe(): Flow<WidgetAreaSettings> = observeRow().map { row -> row?.toDomain() ?: WidgetAreaSettings.Default }

    @Query("SELECT * FROM widget_area_settings WHERE id = 0")
    abstract suspend fun getRow(): WidgetAreaSettingsRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(settings: WidgetAreaSettingsRecord)

    @Transaction
    open suspend fun update(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
        val current: WidgetAreaSettings = getRow()?.toDomain() ?: WidgetAreaSettings.Default
        upsert(WidgetAreaSettingsRecord.from(transform(current)))
    }
}
