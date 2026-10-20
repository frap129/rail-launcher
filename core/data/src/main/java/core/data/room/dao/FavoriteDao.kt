package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import core.data.favorites.FavoriteRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteRecord>>

    @Query(
        "INSERT OR IGNORE INTO favorites (key, position) " +
            "SELECT :key, COALESCE(MAX(position), -1) + 1 FROM favorites"
    )
    suspend fun add(key: String)

    @Query("DELETE FROM favorites WHERE key IN (:keys)")
    suspend fun remove(keys: List<String>)
}
