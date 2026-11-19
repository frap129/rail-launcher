package core.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import core.data.favorites.FavoriteRecord
import core.data.folders.FolderRecord
import core.data.launches.LaunchCountRecord
import core.data.prefs.AppListOrder
import core.data.prefs.LaunchableCustomization
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.search.SearchHistoryRecord
import core.data.shortcuts.StandaloneShortcutRecord
import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_SDK
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FOLDER_DATABASE_NAME: String = "folder-migration-test"
private const val MIGRATION_START_DATABASE_VERSION: Int = 11
private const val SETTINGS_DATABASE_NAME: String = "settings-migration-test"
private const val SETTINGS_DATABASE_VERSION: Int = 12
private const val STANDALONE_SHORTCUTS_DATABASE_NAME: String = "standalone-shortcuts-migration-test"
private const val STANDALONE_SHORTCUTS_DATABASE_VERSION: Int = 13
private const val SCRUB_EDGES_DATABASE_NAME: String = "scrub-edges-migration-test"
private const val SCRUB_EDGES_DATABASE_VERSION: Int = 14
private const val RAIL_SIDE_DATABASE_NAME: String = "rail-side-migration-test"
private const val RAIL_SIDE_DATABASE_VERSION: Int = 15
private const val LAUNCHABLE_CUSTOMIZATIONS_DATABASE_NAME: String = "launchable-customizations-migration-test"
private const val LAUNCHABLE_CUSTOMIZATIONS_DATABASE_VERSION: Int = 16
private const val SEARCH_ENTRIES_DATABASE_NAME: String = "search-entries-migration-test"
private const val SEARCH_ENTRIES_DATABASE_VERSION: Int = 17
private const val SEARCH_SUGGESTIONS_DATABASE_NAME: String = "search-suggestions-migration-test"
private const val SEARCH_SUGGESTIONS_DATABASE_VERSION: Int = 18
private const val SEARCH_ROWS_DATABASE_NAME: String = "search-rows-migration-test"
private const val SEARCH_ROWS_DATABASE_VERSION: Int = 19
private const val AUTO_LAUNCH_DATABASE_NAME: String = "auto-launch-migration-test"
private const val AUTO_LAUNCH_DATABASE_VERSION: Int = 20
private const val CONTACTS_DATABASE_NAME: String = "contacts-migration-test"
private const val CONTACTS_DATABASE_VERSION: Int = 21
private const val WEB_SUGGESTIONS_DATABASE_NAME: String = "web-suggestions-migration-test"
private const val WEB_SUGGESTIONS_DATABASE_VERSION: Int = 22
private const val SWIPE_DOWN_DATABASE_NAME: String = "swipe-down-migration-test"
private const val SWIPE_DOWN_DATABASE_VERSION: Int = 23
private const val MIGRATION_24_25_DATABASE_NAME: String = "migration-24-25-test"
private const val MIGRATION_24_25_DATABASE_VERSION: Int = 24
private const val MIGRATION_25_26_DATABASE_NAME: String = "migration-25-26-test"
private const val MIGRATION_25_26_DATABASE_VERSION: Int = 25
private const val ROOM_OPEN_DATABASE_NAME: String = "room-open-migration-test"
private const val TABLE_NAME_PLACEHOLDER: String = "\${TABLE_NAME}"
private const val FIRST_FOLDER_ID: Long = 1L
private const val SECOND_FOLDER_ID: Long = 2L
private const val FAVORITE_KEY: String = "com.example.app/.MainActivity_UserHandle{0}"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppDatabaseMigrationTest {
    @Test
    fun `removes the App List flag and keeps every Folder`() {
        withDatabase(
            name = FOLDER_DATABASE_NAME,
            version = MIGRATION_START_DATABASE_VERSION,
            createTables = { db ->
                db.execSQL(
                    "CREATE TABLE ${FolderRecord.TABLE_NAME} (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, show_in_app_list INTEGER NOT NULL)"
                )
                db.execSQL("INSERT INTO ${FolderRecord.TABLE_NAME} (id, show_in_app_list) VALUES ($FIRST_FOLDER_ID, 1)")
                db.execSQL("INSERT INTO ${FolderRecord.TABLE_NAME} (id, show_in_app_list) VALUES ($SECOND_FOLDER_ID, 0)")
            }
        ) { db ->
            MIGRATION_11_12.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = FolderRecord.TABLE_NAME)
            val actualIds: List<Long> = db.folderIds()

            assertEquals(listOf("id"), actualColumns)
            assertEquals(listOf(FIRST_FOLDER_ID, SECOND_FOLDER_ID), actualIds)
        }
    }

    @Test
    fun `adds the Private Space switch and keeps the settings row`() {
        withDatabase(
            name = SETTINGS_DATABASE_NAME,
            version = SETTINGS_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(iconPack = TEST_ICON_PACK_PACKAGE)
            }
        ) { db ->
            MIGRATION_12_13.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = Settings.TABLE_NAME)
            val actualSwitch: Boolean = db.hidePrivateSpaceWhenLocked()
            val actualIconPack: String? = db.iconPack()

            assertEquals(listOf("id", "icon_pack", Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED), actualColumns)
            assertEquals(false, actualSwitch)
            assertEquals(TEST_ICON_PACK_PACKAGE, actualIconPack)
        }
    }

    @Test
    fun `adds the Standalone shortcut table and keeps the Favorites`() {
        withDatabase(
            name = STANDALONE_SHORTCUTS_DATABASE_NAME,
            version = STANDALONE_SHORTCUTS_DATABASE_VERSION,
            createTables = { db ->
                db.execSQL(
                    "CREATE TABLE ${FavoriteRecord.TABLE_NAME} (key TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(key))"
                )
                db.execSQL("INSERT INTO ${FavoriteRecord.TABLE_NAME} (key, position) VALUES ('$FAVORITE_KEY', 0)")
            }
        ) { db ->
            MIGRATION_13_14.migrate(db)

            assertEquals(listOf("key", "added_at"), db.columnNames(table = StandaloneShortcutRecord.TABLE_NAME))
            assertEquals(listOf(FAVORITE_KEY), db.favoriteKeys())
        }
    }

    @Test
    fun `adds Scrub edges defaulting to both edges and keeps the settings row`() {
        withDatabase(
            name = SCRUB_EDGES_DATABASE_NAME,
            version = SCRUB_EDGES_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(iconPack = TEST_ICON_PACK_PACKAGE, hidePrivateSpaceWhenLocked = true)
            }
        ) { db ->
            MIGRATION_14_15.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = Settings.TABLE_NAME)
            val actualScrubEdges: String = db.scrubEdges()
            val actualIconPack: String? = db.iconPack()
            val actualSwitch: Boolean = db.hidePrivateSpaceWhenLocked()

            assertEquals(
                listOf("id", "icon_pack", Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED, Settings.COLUMN_SCRUB_EDGES),
                actualColumns
            )
            assertEquals("BOTH", actualScrubEdges)
            assertEquals(TEST_ICON_PACK_PACKAGE, actualIconPack)
            assertEquals(true, actualSwitch)
        }
    }

    @Test
    fun `adds the nullable Custom icon and keeps every Launchable customization`() {
        withDatabase(
            name = LAUNCHABLE_CUSTOMIZATIONS_DATABASE_NAME,
            version = LAUNCHABLE_CUSTOMIZATIONS_DATABASE_VERSION,
            createTables = { db ->
                db.execSQL("CREATE TABLE ${LaunchableCustomization.TABLE_NAME} (key TEXT NOT NULL, name TEXT, PRIMARY KEY(key))")
                db.execSQL("INSERT INTO ${LaunchableCustomization.TABLE_NAME} (key, name) VALUES ('$FAVORITE_KEY', 'Renamed')")
            }
        ) { db ->
            MIGRATION_16_17.migrate(db)

            assertEquals(listOf("key", "name", "icon"), db.columnNames(table = LaunchableCustomization.TABLE_NAME))
            assertEquals("Renamed", db.launchableCustomizationName())
            assertEquals(null, db.launchableCustomizationIcon())
        }
    }

    @Test
    fun `adds the Search entries defaulting swipe up on and Home press off and keeps the settings row`() {
        withDatabase(
            name = SEARCH_ENTRIES_DATABASE_NAME,
            version = SEARCH_ENTRIES_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START
                )
            }
        ) { db ->
            MIGRATION_17_18.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = Settings.TABLE_NAME)
            val actualSwipeUp: Boolean = db.openSearchOnSwipeUp()
            val actualHomePress: Boolean = db.openSearchOnHomePress()

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS
                ),
                actualColumns
            )
            assertEquals(true, actualSwipeUp)
            assertEquals(false, actualHomePress)
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
        }
    }

    @Test
    fun `adds the Search suggestions switch defaulting on and the Search history table keeps the settings row`() {
        withDatabase(
            name = SEARCH_SUGGESTIONS_DATABASE_NAME,
            version = SEARCH_SUGGESTIONS_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START,
                    openSearchOnSwipeUp = false,
                    openSearchOnHomePress = true
                )
            }
        ) { db ->
            MIGRATION_18_19.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS,
                    Settings.COLUMN_SEARCH_SUGGESTIONS
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(true, db.searchSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(true, db.openSearchOnHomePress())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
            assertEquals(
                listOf("key", "launch_count", "last_launched_at", "removed"),
                db.columnNames(table = SearchHistoryRecord.TABLE_NAME)
            )
        }
    }

    @Test
    fun `adds the Calculator and web rows on, the Play Store row off and no Search engine, and keeps the settings row`() {
        withDatabase(
            name = SEARCH_ROWS_DATABASE_NAME,
            version = SEARCH_ROWS_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START,
                    openSearchOnSwipeUp = false,
                    openSearchOnHomePress = true,
                    searchSuggestions = false
                )
            }
        ) { db ->
            MIGRATION_19_20.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS,
                    Settings.COLUMN_SEARCH_SUGGESTIONS,
                    Settings.COLUMN_SEARCH_CALCULATOR_ROW,
                    Settings.COLUMN_SEARCH_WEB_ROW,
                    Settings.COLUMN_SEARCH_PLAY_STORE_ROW,
                    Settings.COLUMN_SEARCH_ENGINE,
                    Settings.COLUMN_SEARCH_ENGINE_TEMPLATE
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(true, db.searchCalculatorRow())
            assertEquals(true, db.searchWebRow())
            assertEquals(false, db.searchPlayStoreRow())
            assertEquals(SearchEngine.None.name, db.searchEngine())
            assertNull(db.searchEngineTemplate())
            assertEquals(false, db.searchSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(true, db.openSearchOnHomePress())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
        }
    }

    @Test
    fun `adds Auto-launch and Bottom-anchored results defaulting off and keeps the settings row`() {
        withDatabase(
            name = AUTO_LAUNCH_DATABASE_NAME,
            version = AUTO_LAUNCH_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START,
                    openSearchOnSwipeUp = false,
                    openSearchOnHomePress = true,
                    searchSuggestions = false,
                    searchCalculatorRow = false,
                    searchWebRow = false,
                    searchPlayStoreRow = false,
                    searchEngine = SearchEngine.Kagi,
                    searchEngineTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
                )
            }
        ) { db ->
            MIGRATION_20_21.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS,
                    Settings.COLUMN_SEARCH_SUGGESTIONS,
                    Settings.COLUMN_SEARCH_CALCULATOR_ROW,
                    Settings.COLUMN_SEARCH_WEB_ROW,
                    Settings.COLUMN_SEARCH_PLAY_STORE_ROW,
                    Settings.COLUMN_SEARCH_ENGINE,
                    Settings.COLUMN_SEARCH_ENGINE_TEMPLATE,
                    Settings.COLUMN_SEARCH_AUTO_LAUNCH,
                    Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(false, db.searchAutoLaunch())
            assertEquals(false, db.searchBottomAnchoredResults())
            assertEquals(false, db.searchCalculatorRow())
            assertEquals(false, db.searchWebRow())
            assertEquals(false, db.searchPlayStoreRow())
            assertEquals(SearchEngine.Kagi.name, db.searchEngine())
            assertEquals(TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE, db.searchEngineTemplate())
            assertEquals(false, db.searchSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(true, db.openSearchOnHomePress())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
        }
    }

    @Test
    fun `adds the Contact results switch defaulting off and keeps the settings row`() {
        withDatabase(
            name = CONTACTS_DATABASE_NAME,
            version = CONTACTS_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START,
                    openSearchOnSwipeUp = false,
                    openSearchOnHomePress = true,
                    searchSuggestions = false,
                    searchCalculatorRow = false,
                    searchWebRow = false,
                    searchPlayStoreRow = false,
                    searchAutoLaunch = true,
                    searchBottomAnchoredResults = true,
                    searchEngine = SearchEngine.Kagi,
                    searchEngineTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
                )
            }
        ) { db ->
            MIGRATION_21_22.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS,
                    Settings.COLUMN_SEARCH_SUGGESTIONS,
                    Settings.COLUMN_SEARCH_CALCULATOR_ROW,
                    Settings.COLUMN_SEARCH_WEB_ROW,
                    Settings.COLUMN_SEARCH_PLAY_STORE_ROW,
                    Settings.COLUMN_SEARCH_ENGINE,
                    Settings.COLUMN_SEARCH_ENGINE_TEMPLATE,
                    Settings.COLUMN_SEARCH_AUTO_LAUNCH,
                    Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS,
                    Settings.COLUMN_SEARCH_CONTACTS
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(false, db.searchContacts())
            assertEquals(true, db.searchAutoLaunch())
            assertEquals(true, db.searchBottomAnchoredResults())
            assertEquals(false, db.searchCalculatorRow())
            assertEquals(false, db.searchWebRow())
            assertEquals(false, db.searchPlayStoreRow())
            assertEquals(SearchEngine.Kagi.name, db.searchEngine())
            assertEquals(TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE, db.searchEngineTemplate())
            assertEquals(false, db.searchSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(true, db.openSearchOnHomePress())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
        }
    }

    @Test
    fun `adds the Live web suggestions switch defaulting off and keeps the settings row`() {
        withDatabase(
            name = WEB_SUGGESTIONS_DATABASE_NAME,
            version = WEB_SUGGESTIONS_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START,
                    railSide = RailSide.START,
                    openSearchOnSwipeUp = false,
                    openSearchOnHomePress = true,
                    searchSuggestions = false,
                    searchCalculatorRow = false,
                    searchWebRow = false,
                    searchPlayStoreRow = false,
                    searchAutoLaunch = true,
                    searchBottomAnchoredResults = true,
                    searchContacts = true,
                    searchEngine = SearchEngine.Kagi,
                    searchEngineTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
                )
            }
        ) { db ->
            MIGRATION_22_23.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE,
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS,
                    Settings.COLUMN_SEARCH_SUGGESTIONS,
                    Settings.COLUMN_SEARCH_CALCULATOR_ROW,
                    Settings.COLUMN_SEARCH_WEB_ROW,
                    Settings.COLUMN_SEARCH_PLAY_STORE_ROW,
                    Settings.COLUMN_SEARCH_ENGINE,
                    Settings.COLUMN_SEARCH_ENGINE_TEMPLATE,
                    Settings.COLUMN_SEARCH_AUTO_LAUNCH,
                    Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS,
                    Settings.COLUMN_SEARCH_CONTACTS,
                    Settings.COLUMN_SEARCH_WEB_SUGGESTIONS
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(false, db.searchWebSuggestions())
            assertEquals(true, db.searchContacts())
            assertEquals(true, db.searchAutoLaunch())
            assertEquals(true, db.searchBottomAnchoredResults())
            assertEquals(false, db.searchCalculatorRow())
            assertEquals(false, db.searchWebRow())
            assertEquals(false, db.searchPlayStoreRow())
            assertEquals(SearchEngine.Kagi.name, db.searchEngine())
            assertEquals(TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE, db.searchEngineTemplate())
            assertEquals(false, db.searchSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(true, db.openSearchOnHomePress())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(true, db.hidePrivateSpaceWhenLocked())
            assertEquals("START", db.scrubEdges())
            assertEquals("START", db.railSide())
        }
    }

    @Test
    fun `adds the Swipe down on Home switch defaulting on and keeps the settings row`() {
        withDatabase(
            name = SWIPE_DOWN_DATABASE_NAME,
            version = SWIPE_DOWN_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    openSearchOnSwipeUp = false,
                    searchWebSuggestions = true
                )
            }
        ) { db ->
            MIGRATION_23_24.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_SEARCH_WEB_SUGGESTIONS,
                    Settings.COLUMN_EXPAND_NOTIFICATIONS_ON_SWIPE_DOWN
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(true, db.expandNotificationsOnSwipeDown())
            assertEquals(true, db.searchWebSuggestions())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
        }
    }

    @Test
    fun `adds the Launch count table, the App List order defaulting to Recently used and Rank Folders by use defaulting to on`() {
        withDatabase(
            name = MIGRATION_24_25_DATABASE_NAME,
            version = MIGRATION_24_25_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(iconPack = TEST_ICON_PACK_PACKAGE, openSearchOnSwipeUp = false)
            }
        ) { db ->
            MIGRATION_24_25.migrate(db)

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP,
                    Settings.COLUMN_APP_LIST_ORDER,
                    Settings.COLUMN_RANK_FOLDERS_BY_USE
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals("RECENTLY_USED", db.appListOrder())
            assertEquals(true, db.rankFoldersByUse())
            assertEquals(false, db.openSearchOnSwipeUp())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
            assertEquals(
                listOf(
                    TableColumn(name = "key", type = "TEXT", isNotNull = true, isPrimaryKey = true),
                    TableColumn(name = "launch_count", type = "INTEGER", isNotNull = true, isPrimaryKey = false),
                    TableColumn(name = "recent_use_score", type = "REAL", isNotNull = true, isPrimaryKey = false),
                    TableColumn(name = "score_updated_at", type = "INTEGER", isNotNull = true, isPrimaryKey = false)
                ),
                db.tableColumns(table = LaunchCountRecord.TABLE_NAME)
            )
        }
    }

    @Test
    fun `adds the Hidden flag and Show hidden apps with their defaults and keeps every customization and setting`() {
        withDatabase(
            name = MIGRATION_25_26_DATABASE_NAME,
            version = MIGRATION_25_26_DATABASE_VERSION,
            createTables = { db ->
                db.execSQL(
                    "CREATE TABLE ${LaunchableCustomization.TABLE_NAME} (key TEXT NOT NULL, name TEXT, icon TEXT, PRIMARY KEY(key))"
                )
                db.execSQL(
                    "INSERT INTO ${LaunchableCustomization.TABLE_NAME} (key, name, icon) VALUES ('$FAVORITE_KEY', 'Renamed', 'icon')"
                )
                db.createSettingsRow(iconPack = TEST_ICON_PACK_PACKAGE, searchSuggestions = false)
            }
        ) { db ->
            MIGRATION_25_26.migrate(db)

            assertEquals(
                listOf("key", "name", "icon", LaunchableCustomization.COLUMN_HIDDEN),
                db.columnNames(table = LaunchableCustomization.TABLE_NAME)
            )
            assertEquals("Renamed", db.launchableCustomizationName())
            assertEquals("icon", db.launchableCustomizationIcon())
            assertEquals(false, db.launchableCustomizationHidden())
            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_SEARCH_SUGGESTIONS,
                    Settings.COLUMN_SEARCH_SHOW_HIDDEN_APPS
                ),
                db.columnNames(table = Settings.TABLE_NAME)
            )
            assertEquals(true, db.searchShowHiddenApps())
            assertEquals(false, db.searchSuggestions())
            assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
        }
    }

    @Test
    fun `adds Show Recently installed defaulting off and keeps the settings row`() {
        assertAddsBooleanSettingColumn(
            migration = MIGRATION_26_27,
            version = 26,
            column = Settings.COLUMN_SHOW_RECENTLY_INSTALLED,
            expected = false
        )
    }

    @Test
    fun `adds Rail haptics defaulting on and keeps the settings row`() {
        assertAddsBooleanSettingColumn(
            migration = MIGRATION_27_28,
            version = 27,
            column = Settings.COLUMN_RAIL_HAPTICS,
            expected = true
        )
    }

    @Test
    fun `adds Show Rail defaulting on and keeps the settings row`() {
        assertAddsBooleanSettingColumn(
            migration = MIGRATION_28_29,
            version = 28,
            column = Settings.COLUMN_SHOW_RAIL,
            expected = true
        )
    }

    @Test
    fun `adds Suggest often-used Apps defaulting on and keeps the settings row`() {
        assertAddsBooleanSettingColumn(
            migration = MIGRATION_29_30,
            version = 29,
            column = Settings.COLUMN_SUGGEST_OFTEN_USED_APPS,
            expected = true
        )
    }

    @Test
    fun `adds Rail side defaulting to the layout end and keeps the Scrub edges`() {
        withDatabase(
            name = RAIL_SIDE_DATABASE_NAME,
            version = RAIL_SIDE_DATABASE_VERSION,
            createTables = { db ->
                db.createSettingsRow(
                    iconPack = TEST_ICON_PACK_PACKAGE,
                    hidePrivateSpaceWhenLocked = true,
                    scrubEdges = ScrubEdges.START
                )
            }
        ) { db ->
            MIGRATION_15_16.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = Settings.TABLE_NAME)
            val actualRailSide: String = db.railSide()
            val actualScrubEdges: String = db.scrubEdges()
            val actualIconPack: String? = db.iconPack()
            val actualSwitch: Boolean = db.hidePrivateSpaceWhenLocked()

            assertEquals(
                listOf(
                    "id",
                    "icon_pack",
                    Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED,
                    Settings.COLUMN_SCRUB_EDGES,
                    Settings.COLUMN_RAIL_SIDE
                ),
                actualColumns
            )
            assertEquals("END", actualRailSide)
            assertEquals("START", actualScrubEdges)
            assertEquals(TEST_ICON_PACK_PACKAGE, actualIconPack)
            assertEquals(true, actualSwitch)
        }
    }

    @Test
    fun `opens a main database with Room after migrating it to the current version`() {
        assertOpensAfterMigration(version = SEARCH_ENTRIES_DATABASE_VERSION)
    }

    @Test
    fun `opens the oldest restorable database with Room after migrating it to the current version`() {
        assertOpensAfterMigration(version = MIGRATION_START_DATABASE_VERSION)
    }
}

/** Opens [name] at [version] with [createTables], runs [testBody] against it and removes it again. */
private fun withDatabase(
    name: String,
    version: Int,
    createTables: (SupportSQLiteDatabase) -> Unit,
    testBody: (SupportSQLiteDatabase) -> Unit
) {
    val context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(name)
    val callback: SupportSQLiteOpenHelper.Callback = object : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            createTables(db)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val configuration: SupportSQLiteOpenHelper.Configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(name)
        .callback(callback)
        .build()
    val helper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    try {
        testBody(helper.writableDatabase)
    } finally {
        helper.close()
        context.deleteDatabase(name)
    }
}

/** A Settings column a migration test creates, with its definition and stored value. */
private data class SettingsColumn(val name: String, val definition: String, val value: String)

/**
 * Creates [name] at [version] from its exported Room schema, runs [insertRows] on it and closes it,
 * so Room can migrate and validate it when the test opens it.
 */
private fun createSchemaDatabase(name: String, version: Int, insertRows: (SupportSQLiteDatabase) -> Unit = {}) {
    val context: Context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(name)
    val schemaResource: URL =
        checkNotNull(AppDatabaseMigrationTest::class.java.classLoader?.getResource("${AppDatabase::class.java.name}/$version.json")) {
            "Missing the exported Room schema for version $version"
        }
    val entities: JSONArray = JSONObject(schemaResource.readText()).getJSONObject("database").getJSONArray("entities")
    val callback: SupportSQLiteOpenHelper.Callback = object : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            for (index in 0 until entities.length()) {
                val entity: JSONObject = entities.getJSONObject(index)
                val tableName: String = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, tableName))
                val indices: JSONArray = entity.optJSONArray("indices") ?: JSONArray()
                for (indexPosition in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(indexPosition).getString("createSql").replace(TABLE_NAME_PLACEHOLDER, tableName))
                }
            }
            insertRows(db)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val configuration: SupportSQLiteOpenHelper.Configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(name)
        .callback(callback)
        .build()
    FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper -> helper.writableDatabase }
}

/** Creates [version]'s schema, opens it with Room and asserts the migration kept its settings and chose no engine. */
private fun assertOpensAfterMigration(version: Int) {
    val inputContext: Context = RuntimeEnvironment.getApplication()
    val inputDatabaseName: String = "$ROOM_OPEN_DATABASE_NAME-$version"
    createSchemaDatabase(name = inputDatabaseName, version = version) { db ->
        db.execSQL("INSERT INTO ${Settings.TABLE_NAME} (id, icon_pack) VALUES (0, '$TEST_ICON_PACK_PACKAGE')")
    }
    val actualDatabase: AppDatabase = Room.databaseBuilder(inputContext, AppDatabase::class.java, inputDatabaseName)
        .addMigrations(*APP_DATABASE_MIGRATIONS)
        .build()
    try {
        val actualSettings: Triple<String?, String, String> = actualDatabase.openHelper.writableDatabase
            .query("SELECT icon_pack, ${Settings.COLUMN_SEARCH_ENGINE}, ${Settings.COLUMN_APP_LIST_ORDER} FROM ${Settings.TABLE_NAME}")
            .use { cursor ->
                cursor.moveToFirst()
                Triple(
                    cursor.getString(cursor.getColumnIndexOrThrow("icon_pack")),
                    cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_ENGINE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_APP_LIST_ORDER))
                )
            }
        assertEquals("Version $version kept its settings", TEST_ICON_PACK_PACKAGE, actualSettings.first)
        assertEquals("Version $version chose no engine", SearchEngine.None.name, actualSettings.second)
        assertEquals("Version $version chose Recently used", AppListOrder.DEFAULT.name, actualSettings.third)
    } finally {
        actualDatabase.close()
        inputContext.deleteDatabase(inputDatabaseName)
    }
}

/**
 * Runs [migration] on a settings row created at [version] and asserts it added [column], defaulting
 * to [expected], while keeping the other settings.
 */
private fun assertAddsBooleanSettingColumn(migration: Migration, version: Int, column: String, expected: Boolean) {
    withDatabase(
        name = "migration-$version-${version + 1}-test",
        version = version,
        createTables = { db -> db.createSettingsRow(iconPack = TEST_ICON_PACK_PACKAGE, searchSuggestions = false) }
    ) { db ->
        migration.migrate(db)

        assertEquals(
            listOf("id", "icon_pack", Settings.COLUMN_SEARCH_SUGGESTIONS, column),
            db.columnNames(table = Settings.TABLE_NAME)
        )
        assertEquals(expected, db.booleanColumn(column))
        assertEquals(false, db.searchSuggestions())
        assertEquals(TEST_ICON_PACK_PACKAGE, db.iconPack())
    }
}

/** Creates a settings table and inserts one row with the columns each database version had. */
private fun SupportSQLiteDatabase.createSettingsRow(
    iconPack: String,
    hidePrivateSpaceWhenLocked: Boolean? = null,
    scrubEdges: ScrubEdges? = null,
    railSide: RailSide? = null,
    openSearchOnSwipeUp: Boolean? = null,
    openSearchOnHomePress: Boolean? = null,
    searchSuggestions: Boolean? = null,
    searchCalculatorRow: Boolean? = null,
    searchWebRow: Boolean? = null,
    searchPlayStoreRow: Boolean? = null,
    searchAutoLaunch: Boolean? = null,
    searchBottomAnchoredResults: Boolean? = null,
    searchContacts: Boolean? = null,
    searchWebSuggestions: Boolean? = null,
    searchEngine: SearchEngine? = null,
    searchEngineTemplate: String? = null
) {
    val columns: List<SettingsColumn> = listOfNotNull(
        hidePrivateSpaceWhenLocked?.let { value ->
            SettingsColumn(Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        scrubEdges?.let { value ->
            SettingsColumn(Settings.COLUMN_SCRUB_EDGES, "TEXT NOT NULL DEFAULT '${ScrubEdges.DEFAULT.name}'", "'${value.name}'")
        },
        railSide?.let { value ->
            SettingsColumn(Settings.COLUMN_RAIL_SIDE, "TEXT NOT NULL DEFAULT '${RailSide.DEFAULT.name}'", "'${value.name}'")
        },
        openSearchOnSwipeUp?.let { value ->
            SettingsColumn(Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP, "INTEGER NOT NULL DEFAULT 1", value.toSql())
        },
        openSearchOnHomePress?.let { value ->
            SettingsColumn(Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchSuggestions?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_SUGGESTIONS, "INTEGER NOT NULL DEFAULT 1", value.toSql())
        },
        searchCalculatorRow?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_CALCULATOR_ROW, "INTEGER NOT NULL DEFAULT 1", value.toSql())
        },
        searchWebRow?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_WEB_ROW, "INTEGER NOT NULL DEFAULT 1", value.toSql())
        },
        searchPlayStoreRow?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_PLAY_STORE_ROW, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchEngine?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_ENGINE, "TEXT NOT NULL DEFAULT '${SearchEngine.None.name}'", "'${value.name}'")
        },
        searchEngineTemplate?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_ENGINE_TEMPLATE, "TEXT", "'$value'")
        },
        searchAutoLaunch?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_AUTO_LAUNCH, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchBottomAnchoredResults?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchContacts?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_CONTACTS, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchWebSuggestions?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_WEB_SUGGESTIONS, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        }
    )
    val definitions: String = columns.joinToString(separator = "") { column -> ", ${column.name} ${column.definition}" }
    val columnNames: String = columns.joinToString(separator = "") { column -> ", ${column.name}" }
    val values: String = columns.joinToString(separator = "") { column -> ", ${column.value}" }
    execSQL("CREATE TABLE ${Settings.TABLE_NAME} (id INTEGER NOT NULL, icon_pack TEXT$definitions, PRIMARY KEY(id))")
    execSQL("INSERT INTO ${Settings.TABLE_NAME} (id, icon_pack$columnNames) VALUES (0, '$iconPack'$values)")
}

private fun Boolean.toSql(): String = if (this) "1" else "0"

/** A table column a migration test reads, with the shape SQLite reports for it. */
private data class TableColumn(val name: String, val type: String, val isNotNull: Boolean, val isPrimaryKey: Boolean)

private fun SupportSQLiteDatabase.tableColumns(table: String): List<TableColumn> = query("PRAGMA table_info($table)").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(
                TableColumn(
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    isNotNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 0,
                    isPrimaryKey = cursor.getInt(cursor.getColumnIndexOrThrow("pk")) != 0
                )
            )
            cursor.moveToNext()
        }
    }
}

private fun SupportSQLiteDatabase.columnNames(table: String): List<String> = tableColumns(table).map { column -> column.name }

private fun SupportSQLiteDatabase.favoriteKeys(): List<String> =
    query("SELECT key FROM ${FavoriteRecord.TABLE_NAME} ORDER BY position").use { cursor ->
        cursor.moveToFirst()
        buildList {
            while (!cursor.isAfterLast) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("key")))
                cursor.moveToNext()
            }
        }
    }

private fun SupportSQLiteDatabase.folderIds(): List<Long> = query("SELECT id FROM ${FolderRecord.TABLE_NAME} ORDER BY id").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            cursor.moveToNext()
        }
    }
}

private fun SupportSQLiteDatabase.booleanColumn(columnName: String, table: String = Settings.TABLE_NAME): Boolean =
    query("SELECT $columnName FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(columnName)) != 0
    }

private fun SupportSQLiteDatabase.hidePrivateSpaceWhenLocked(): Boolean = booleanColumn(Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED)

private fun SupportSQLiteDatabase.scrubEdges(): String =
    query("SELECT ${Settings.COLUMN_SCRUB_EDGES} FROM ${Settings.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SCRUB_EDGES))
    }

private fun SupportSQLiteDatabase.railSide(): String =
    query("SELECT ${Settings.COLUMN_RAIL_SIDE} FROM ${Settings.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_RAIL_SIDE))
    }

private fun SupportSQLiteDatabase.appListOrder(): String =
    query("SELECT ${Settings.COLUMN_APP_LIST_ORDER} FROM ${Settings.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_APP_LIST_ORDER))
    }

private fun SupportSQLiteDatabase.rankFoldersByUse(): Boolean = booleanColumn(Settings.COLUMN_RANK_FOLDERS_BY_USE)

private fun SupportSQLiteDatabase.openSearchOnSwipeUp(): Boolean = booleanColumn(Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP)

private fun SupportSQLiteDatabase.openSearchOnHomePress(): Boolean = booleanColumn(Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS)

private fun SupportSQLiteDatabase.searchSuggestions(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_SUGGESTIONS)

private fun SupportSQLiteDatabase.searchAutoLaunch(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_AUTO_LAUNCH)

private fun SupportSQLiteDatabase.searchBottomAnchoredResults(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS)

private fun SupportSQLiteDatabase.searchCalculatorRow(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_CALCULATOR_ROW)

private fun SupportSQLiteDatabase.searchWebRow(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_WEB_ROW)

private fun SupportSQLiteDatabase.searchPlayStoreRow(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_PLAY_STORE_ROW)

private fun SupportSQLiteDatabase.searchContacts(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_CONTACTS)

private fun SupportSQLiteDatabase.searchWebSuggestions(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_WEB_SUGGESTIONS)

private fun SupportSQLiteDatabase.searchShowHiddenApps(): Boolean = booleanColumn(Settings.COLUMN_SEARCH_SHOW_HIDDEN_APPS)

private fun SupportSQLiteDatabase.expandNotificationsOnSwipeDown(): Boolean =
    booleanColumn(Settings.COLUMN_EXPAND_NOTIFICATIONS_ON_SWIPE_DOWN)

private fun SupportSQLiteDatabase.searchEngine(): String =
    query("SELECT ${Settings.COLUMN_SEARCH_ENGINE} FROM ${Settings.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_ENGINE))
    }

private fun SupportSQLiteDatabase.searchEngineTemplate(): String? =
    query("SELECT ${Settings.COLUMN_SEARCH_ENGINE_TEMPLATE} FROM ${Settings.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_ENGINE_TEMPLATE))
    }

private fun SupportSQLiteDatabase.iconPack(): String? = query("SELECT icon_pack FROM ${Settings.TABLE_NAME}").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow("icon_pack"))
}

private fun SupportSQLiteDatabase.launchableCustomizationName(): String? =
    query("SELECT name FROM ${LaunchableCustomization.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow("name"))
    }

private fun SupportSQLiteDatabase.launchableCustomizationIcon(): String? =
    query("SELECT icon FROM ${LaunchableCustomization.TABLE_NAME}").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow("icon"))
    }

private fun SupportSQLiteDatabase.launchableCustomizationHidden(): Boolean =
    booleanColumn(columnName = LaunchableCustomization.COLUMN_HIDDEN, table = LaunchableCustomization.TABLE_NAME)
