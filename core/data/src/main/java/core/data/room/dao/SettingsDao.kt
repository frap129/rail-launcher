package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class SettingsDao : ISingleRowDao<Settings> {
    @Query("SELECT * FROM settings WHERE id = 0")
    abstract fun observeRow(): Flow<Settings?>

    open fun observe(): Flow<Settings> = observeRow().map { it ?: Settings() }

    @Query("SELECT * FROM settings WHERE id = 0")
    abstract override suspend fun getRow(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override suspend fun upsert(row: Settings)

    @Transaction
    open suspend fun update(transform: (Settings) -> Settings) {
        updateRow(defaultRow = Settings(), transform = transform)
    }
}
