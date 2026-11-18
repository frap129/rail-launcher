package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Settings.TABLE_NAME)
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
    @ColumnInfo(name = Settings.COLUMN_SEARCH_PLAY_STORE_ROW, defaultValue = "0")
    val searchPlayStoreRow: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_WEB_SUGGESTIONS, defaultValue = "0")
    val searchWebSuggestions: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_CONTACTS, defaultValue = "0")
    val searchContacts: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_SHOW_HIDDEN_APPS, defaultValue = "1")
    val searchShowHiddenApps: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_ENGINE, defaultValue = "None")
    val searchEngine: SearchEngine = SearchEngine.None,
    @ColumnInfo(name = Settings.COLUMN_SEARCH_ENGINE_TEMPLATE)
    val searchEngineTemplate: String? = null,
    @ColumnInfo(name = Settings.COLUMN_EXPAND_NOTIFICATIONS_ON_SWIPE_DOWN, defaultValue = "1")
    val expandNotificationsOnSwipeDown: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_APP_LIST_ORDER, defaultValue = "RECENTLY_USED")
    val appListOrder: AppListOrder = AppListOrder.DEFAULT,
    @ColumnInfo(name = Settings.COLUMN_RANK_FOLDERS_BY_USE, defaultValue = "1")
    val rankFoldersByUse: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SHOW_RECENTLY_INSTALLED, defaultValue = "0")
    val showRecentlyInstalled: Boolean = false,
    @ColumnInfo(name = Settings.COLUMN_RAIL_HAPTICS, defaultValue = "1")
    val railHaptics: Boolean = true,
    @ColumnInfo(name = Settings.COLUMN_SHOW_RAIL, defaultValue = "1")
    val showRail: Boolean = true
) {
    companion object {
        const val TABLE_NAME: String = "settings"
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
        const val COLUMN_SEARCH_WEB_SUGGESTIONS: String = "search_web_suggestions"
        const val COLUMN_SEARCH_CONTACTS: String = "search_contacts"
        const val COLUMN_SEARCH_SHOW_HIDDEN_APPS: String = "search_show_hidden_apps"
        const val COLUMN_SEARCH_ENGINE: String = "search_engine"
        const val COLUMN_SEARCH_ENGINE_TEMPLATE: String = "search_engine_template"
        const val COLUMN_EXPAND_NOTIFICATIONS_ON_SWIPE_DOWN: String = "expand_notifications_on_swipe_down"
        const val COLUMN_APP_LIST_ORDER: String = "app_list_order"
        const val COLUMN_RANK_FOLDERS_BY_USE: String = "rank_folders_by_use"
        const val COLUMN_SHOW_RECENTLY_INSTALLED: String = "show_recently_installed"
        const val COLUMN_RAIL_HAPTICS: String = "rail_haptics"
        const val COLUMN_SHOW_RAIL: String = "show_rail"
    }
}
