package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.launches.LaunchCountRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LaunchCountDao {
    @Query("SELECT * FROM ${LaunchCountRecord.TABLE_NAME}")
    abstract fun observeAll(): Flow<List<LaunchCountRecord>>

    @Query("SELECT * FROM ${LaunchCountRecord.TABLE_NAME} WHERE key = :key")
    abstract suspend fun get(key: String): LaunchCountRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(record: LaunchCountRecord)

    @Query("DELETE FROM ${LaunchCountRecord.TABLE_NAME} WHERE key IN (:keys)")
    abstract suspend fun remove(keys: List<String>)

    /** Counts a launch of [key] at [launchedAt], decaying its stored score to that time and adding this launch. */
    @Transaction
    open suspend fun record(key: String, launchedAt: Long) {
        val previous: LaunchCountRecord? = get(key = key)
        upsert(
            LaunchCountRecord(
                key = key,
                launchCount = (previous?.launchCount ?: 0) + 1,
                recentUseScore = (previous?.recentUseScoreAt(nowEpochMs = launchedAt) ?: 0.0) + 1.0,
                scoreUpdatedAt = launchedAt
            )
        )
    }
}
