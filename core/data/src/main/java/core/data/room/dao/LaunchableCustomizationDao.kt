package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
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
}
