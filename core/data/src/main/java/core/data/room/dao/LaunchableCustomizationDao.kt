package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import core.data.icons.model.CustomIcon
import core.data.prefs.LaunchableCustomization
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LaunchableCustomizationDao {
    @Query("SELECT * FROM launchable_customizations")
    abstract fun observeAll(): Flow<List<LaunchableCustomization>>

    @Query("SELECT * FROM launchable_customizations WHERE key = :key")
    abstract suspend fun get(key: String): LaunchableCustomization?

    @Query("SELECT * FROM launchable_customizations WHERE key IN (:keys)")
    abstract suspend fun get(keys: List<String>): List<LaunchableCustomization>

    @Query(
        "INSERT INTO launchable_customizations (key, name) VALUES (:key, :name) " +
            "ON CONFLICT(key) DO UPDATE SET name = :name"
    )
    abstract suspend fun setName(key: String, name: String)

    @Query(
        "INSERT INTO launchable_customizations (key, icon) VALUES (:key, :icon) " +
            "ON CONFLICT(key) DO UPDATE SET icon = :icon"
    )
    abstract suspend fun setIcon(key: String, icon: CustomIcon)

    @Query("UPDATE launchable_customizations SET name = NULL WHERE key = :key")
    protected abstract suspend fun clearName(key: String)

    @Query("UPDATE launchable_customizations SET icon = NULL WHERE key = :key")
    protected abstract suspend fun clearIcon(key: String)

    @Query("DELETE FROM launchable_customizations WHERE key = :key AND name IS NULL AND icon IS NULL")
    protected abstract suspend fun deleteIfEmpty(key: String)

    @Transaction
    open suspend fun resetName(key: String) {
        clearName(key = key)
        deleteIfEmpty(key = key)
    }

    @Transaction
    open suspend fun resetIcon(key: String) {
        clearIcon(key = key)
        deleteIfEmpty(key = key)
    }

    @Query("DELETE FROM launchable_customizations WHERE key = :key")
    abstract suspend fun delete(key: String)

    @Query("DELETE FROM launchable_customizations WHERE key IN (:keys)")
    abstract suspend fun delete(keys: List<String>)
}
