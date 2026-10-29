package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT key FROM favorites")
    suspend fun getAllKeys(): List<String>

    @Query("INSERT OR IGNORE INTO favorites (key, position) SELECT :newKey, position FROM favorites WHERE key = :oldKey")
    suspend fun copyKey(oldKey: String, newKey: String)

    @Query("DELETE FROM favorites WHERE key = :oldKey")
    suspend fun deleteKey(oldKey: String)

    /** Moves [oldKey] to [newKey], keeping an existing [newKey] row when there is one. */
    @Transaction
    suspend fun replaceKey(oldKey: String, newKey: String) = replaceStoredKey(
        oldKey = oldKey,
        newKey = newKey,
        copy = { old, new -> copyKey(oldKey = old, newKey = new) },
        delete = { key -> deleteKey(oldKey = key) }
    )

    /** Rewrites the positions of [records] in one transaction. Records no longer stored are ignored. */
    @Update
    suspend fun update(records: List<FavoriteRecord>)
}
