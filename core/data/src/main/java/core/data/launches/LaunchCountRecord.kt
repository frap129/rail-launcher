package core.data.launches

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The launches Rail recorded for one Launchable or Shortcut key, or Pop-up opens for a Folder key. */
@Entity(tableName = "launch_counts")
data class LaunchCountRecord(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "recent_use_score") val recentUseScore: Double,
    @ColumnInfo(name = "score_updated_at") val scoreUpdatedAt: Long
) {
    /** The Recent use score at [nowEpochMs], decayed from when it was last updated. */
    internal fun recentUseScoreAt(nowEpochMs: Long): Double =
        decayRecentUseScore(score = recentUseScore, scoreUpdatedAt = scoreUpdatedAt, nowEpochMs = nowEpochMs)
}
