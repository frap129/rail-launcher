package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.prefs.EventSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class EventSettingsDao : ISingleRowDao<EventSettings> {
    @Query("SELECT * FROM event_settings WHERE id = 0")
    abstract fun observeRow(): Flow<EventSettings?>

    fun observe(): Flow<EventSettings> = observeRow().map { settings -> (settings ?: EventSettings()).withValidMaxEvents() }

    @Query("SELECT * FROM event_settings WHERE id = 0")
    abstract override suspend fun getRow(): EventSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override suspend fun upsert(row: EventSettings)

    @Transaction
    open suspend fun update(transform: (EventSettings) -> EventSettings) {
        updateRow(defaultRow = EventSettings(), transform = { settings -> transform(settings).withValidMaxEvents() })
    }
}
