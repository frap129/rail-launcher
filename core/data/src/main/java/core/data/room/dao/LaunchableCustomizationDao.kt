package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import core.data.prefs.LaunchableCustomization
import kotlinx.coroutines.flow.Flow

@Dao
interface LaunchableCustomizationDao {
    @Query("SELECT * FROM launchable_customizations")
    fun observeAll(): Flow<List<LaunchableCustomization>>

    @Query(
        "INSERT INTO launchable_customizations (key, name) VALUES (:key, :name) " +
            "ON CONFLICT(key) DO UPDATE SET name = :name"
    )
    suspend fun setName(key: String, name: String)

    @Query("DELETE FROM launchable_customizations WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM launchable_customizations WHERE key IN (:keys)")
    suspend fun delete(keys: List<String>)

    @Query("SELECT key FROM launchable_customizations")
    suspend fun getAllKeys(): List<String>

    @Query(
        "INSERT OR IGNORE INTO launchable_customizations (key, name) " +
            "SELECT :newKey, name FROM launchable_customizations WHERE key = :oldKey"
    )
    suspend fun copyKey(oldKey: String, newKey: String)

    @Query("DELETE FROM launchable_customizations WHERE key = :oldKey")
    suspend fun deleteKey(oldKey: String)

    /** Moves [oldKey] to [newKey], keeping an existing [newKey] row when there is one. */
    @Transaction
    suspend fun replaceKey(oldKey: String, newKey: String) = replaceStoredKey(
        oldKey = oldKey,
        newKey = newKey,
        copy = { old, new -> copyKey(oldKey = old, newKey = new) },
        delete = { key -> deleteKey(oldKey = key) }
    )
}
