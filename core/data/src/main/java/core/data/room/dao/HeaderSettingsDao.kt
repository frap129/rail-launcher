package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.prefs.HeaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class HeaderSettingsDao {
    @Query("SELECT * FROM header_settings WHERE id = 0")
    abstract fun observeRow(): Flow<HeaderSettings?>

    open fun observe(): Flow<HeaderSettings> = observeRow().map { settings -> settings ?: HeaderSettings() }

    @Query("SELECT * FROM header_settings WHERE id = 0")
    abstract suspend fun getRow(): HeaderSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(settings: HeaderSettings)

    @Transaction
    open suspend fun update(transform: (HeaderSettings) -> HeaderSettings) {
        upsert(transform(getRow() ?: HeaderSettings()))
    }
}
