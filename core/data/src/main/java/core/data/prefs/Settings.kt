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
    val searchSuggestions: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_AUTO_LAUNCH, defaultValue = "0")
    val searchAutoLaunch: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS, defaultValue = "0")
    val searchBottomAnchoredResults: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_CALCULATOR_ROW, defaultValue = "1")
    val searchCalculatorRow: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_WEB_ROW, defaultValue = "1")
    val searchWebRow: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_PLAY_STORE_ROW, defaultValue = "1")
    val searchPlayStoreRow: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_CONTACTS, defaultValue = "0")
    val searchContacts: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_ENGINE, defaultValue = "GOOGLE")
    val searchEngine: SearchEngine = SearchEngine.Google,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_ENGINE_TEMPLATE)
    val searchEngineTemplate: String? = null
) {
    companion object {
        const val COLUMN_ICON_PACK: String = "icon_pack"
        const val COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED: String = "hide_private_space_when_locked"
        const val COLUMN_SCRUB_EDGES: String = "scrub_edges"
        const val COLUMN_RAIL_SIDE: String = "rail_side"
        const val COLUMN_OPEN_SEARCH_ON_SWIPE_UP: String = "open_search_on_swipe_up"
        const val COLUMN_OPEN_SEARCH_ON_HOME_PRESS: String = "open_search_on_home_press"
        const val COLUMN_SEARCH_SUGGESTIONS: String = "search_suggestions"
        const val COLUMN_SEARCH_AUTO_LAUNCH: String = "search_auto_launch"
        const val COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS: String = "search_bottom_anchored_results"
        const val COLUMN_SEARCH_CALCULATOR_ROW: String = "search_calculator_row"
        const val COLUMN_SEARCH_WEB_ROW: String = "search_web_row"
        const val COLUMN_SEARCH_PLAY_STORE_ROW: String = "search_play_store_row"
        const val COLUMN_SEARCH_CONTACTS: String = "search_contacts"
        const val COLUMN_SEARCH_ENGINE: String = "search_engine"
        const val COLUMN_SEARCH_ENGINE_TEMPLATE: String = "search_engine_template"
    }
}
