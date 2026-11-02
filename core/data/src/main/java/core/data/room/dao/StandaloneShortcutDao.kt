package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.data.shortcuts.StandaloneShortcutRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface StandaloneShortcutDao : TransactionalDao {
    @Query("SELECT * FROM standalone_shortcuts ORDER BY added_at, key")
    fun observeAll(): Flow<List<StandaloneShortcutRecord>>

    @Query("SELECT * FROM standalone_shortcuts ORDER BY added_at, key")
    suspend fun getAll(): List<StandaloneShortcutRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM standalone_shortcuts WHERE key = :key)")
    suspend fun exists(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: StandaloneShortcutRecord)

    @Query("DELETE FROM standalone_shortcuts WHERE key IN (:keys)")
    suspend fun delete(keys: List<String>)
}
