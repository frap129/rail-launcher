package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    abstract fun observeRow(): Flow<Settings?>

    open fun observe(): Flow<Settings> = observeRow().map { it ?: Settings() }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(settings: Settings)
}
