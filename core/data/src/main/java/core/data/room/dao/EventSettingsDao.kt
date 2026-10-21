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
abstract class EventSettingsDao {
    @Query("SELECT * FROM event_settings WHERE id = 0")
    abstract fun observeRow(): Flow<EventSettings?>

    fun observe(): Flow<EventSettings> = observeRow().map { settings -> (settings ?: EventSettings()).withValidMaxEvents() }

    @Query("SELECT * FROM event_settings WHERE id = 0")
    abstract suspend fun getRow(): EventSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(settings: EventSettings)

    @Transaction
    open suspend fun update(transform: (EventSettings) -> EventSettings) {
        upsert(transform(getRow() ?: EventSettings()).withValidMaxEvents())
    }
}
