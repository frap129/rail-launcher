package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.search.SearchHistoryRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchHistoryDao {
    @Query("SELECT * FROM search_history")
    abstract fun observeAll(): Flow<List<SearchHistoryRecord>>

    @Query("SELECT * FROM search_history WHERE key = :key")
    abstract suspend fun get(key: String): SearchHistoryRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(record: SearchHistoryRecord)

    @Query("DELETE FROM search_history WHERE key IN (:keys)")
    abstract suspend fun remove(keys: List<String>)

    /** Counts a launch of [key] at [launchedAt] without changing whether it is in Search suggestions. */
    @Transaction
    open suspend fun record(key: String, launchedAt: Long) {
        val previous: SearchHistoryRecord? = get(key = key)
        upsert(
            SearchHistoryRecord(
                key = key,
                launchCount = (previous?.launchCount ?: 0) + 1,
                lastLaunchedAt = launchedAt,
                isRemoved = previous?.isRemoved ?: false
            )
        )
    }

    /** Removes [key] from Search suggestions, keeping its launch count and last launch. */
    @Transaction
    open suspend fun removeFromSuggestions(key: String) {
        val previous: SearchHistoryRecord? = get(key = key)
        upsert(
            SearchHistoryRecord(
                key = key,
                launchCount = previous?.launchCount ?: 0,
                lastLaunchedAt = previous?.lastLaunchedAt ?: 0,
                isRemoved = true
            )
        )
    }
}
