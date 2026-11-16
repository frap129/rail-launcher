package core.data.launches

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.pow

/** The launches Rail recorded for one Launchable or Shortcut key, or Pop-up opens for a Folder key. */
@Entity(tableName = LaunchCountRecord.TABLE_NAME)
data class LaunchCountRecord(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "recent_use_score") val recentUseScore: Double,
    @ColumnInfo(name = "score_updated_at") val scoreUpdatedAt: Long
) {
    /** The Recent use score at [nowEpochMs], decayed from when it was last updated. */
    internal fun recentUseScoreAt(nowEpochMs: Long): Double =
        recentUseScore * 2.0.pow(-halfLivesBetween(fromEpochMs = scoreUpdatedAt, toEpochMs = nowEpochMs))

    companion object {
        const val TABLE_NAME: String = "launch_counts"
    }
}
