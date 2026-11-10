package core.data.search

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The launches Rail recorded from Search for one Launchable. */
@Entity(tableName = "search_history")
data class SearchHistoryRecord(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "last_launched_at") val lastLaunchedAt: Long,
    /** Whether the user removed this Launchable from Search suggestions. */
    @ColumnInfo(name = "removed") val isRemoved: Boolean
)
