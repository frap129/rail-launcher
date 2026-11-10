package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = Settings.COLUMN_ICON_PACK) val iconPack: String? = null,
    @ColumnInfo(name = Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED, defaultValue = "0")
    val hidePrivateSpaceWhenLocked: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SCRUB_EDGES, defaultValue = "BOTH")
    val scrubEdges: ScrubEdges = ScrubEdges.DEFAULT,
    @ColumnInfo(name = Settings.COLUMN_RAIL_SIDE, defaultValue = "END")
    val railSide: RailSide = RailSide.DEFAULT,
    @ColumnInfo(name = Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP, defaultValue = "1")
    val openSearchOnSwipeUp: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS, defaultValue = "0")
    val openSearchOnHomePress: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_SUGGESTIONS, defaultValue = "1")
    val searchSuggestions: Boolean = true
) {
    companion object {
        const val COLUMN_ICON_PACK: String = "icon_pack"
        const val COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED: String = "hide_private_space_when_locked"
        const val COLUMN_SCRUB_EDGES: String = "scrub_edges"
        const val COLUMN_RAIL_SIDE: String = "rail_side"
        const val COLUMN_OPEN_SEARCH_ON_SWIPE_UP: String = "open_search_on_swipe_up"
        const val COLUMN_OPEN_SEARCH_ON_HOME_PRESS: String = "open_search_on_home_press"
        const val COLUMN_SEARCH_SUGGESTIONS: String = "search_suggestions"
    }
}
