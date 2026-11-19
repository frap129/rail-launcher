package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.data.search.StoppedSuggestionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface StoppedSuggestionDao {
    @Query("SELECT * FROM ${StoppedSuggestionRecord.TABLE_NAME}")
    fun observeAll(): Flow<List<StoppedSuggestionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: StoppedSuggestionRecord)

    @Query("DELETE FROM ${StoppedSuggestionRecord.TABLE_NAME}")
    suspend fun clear()

    @Query("DELETE FROM ${StoppedSuggestionRecord.TABLE_NAME} WHERE key IN (:keys)")
    suspend fun remove(keys: List<String>)
}
