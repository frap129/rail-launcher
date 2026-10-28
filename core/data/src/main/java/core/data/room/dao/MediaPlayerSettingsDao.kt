package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.widgets.MediaPlayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class MediaPlayerSettingsDao : ISingleRowDao<MediaPlayerSettings> {
    @Query("SELECT * FROM media_player_settings WHERE id = 0")
    abstract fun observeRow(): Flow<MediaPlayerSettings?>

    fun observe(): Flow<MediaPlayerSettings> = observeRow().map { row -> row ?: MediaPlayerSettings.Default }

    @Query("SELECT * FROM media_player_settings WHERE id = 0")
    abstract override suspend fun getRow(): MediaPlayerSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override suspend fun upsert(row: MediaPlayerSettings)

    @Transaction
    open suspend fun update(transform: (MediaPlayerSettings) -> MediaPlayerSettings) {
        updateRow(defaultRow = MediaPlayerSettings.Default, transform = transform)
    }
}
