package core.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FOLDER_DATABASE_NAME: String = "folder-migration-test"
private const val FOLDERS_DATABASE_VERSION: Int = 11
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
private const val LAUNCHABLE_CUSTOMIZATIONS_TABLE: String = "launchable_customizations"
private const val FIRST_FOLDER_ID: Long = 1L
private const val SECOND_FOLDER_ID: Long = 2L
private const val SETTINGS_TABLE: String = "settings"
private const val SEARCH_HISTORY_TABLE: String = "search_history"
private const val STANDALONE_SHORTCUTS_TABLE: String = "standalone_shortcuts"
private const val FAVORITES_TABLE: String = "favorites"
private const val FAVORITE_KEY: String = "com.example.app/.MainActivity_UserHandle{0}"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppDatabaseMigrationTest {
    @Test
    fun `removes the App List flag and keeps every Folder`() {
        withDatabase(
            name = FOLDER_DATABASE_NAME,
            version = FOLDERS_DATABASE_VERSION,
            createTables = { db ->
                db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, show_in_app_list INTEGER NOT NULL)")
                db.execSQL("INSERT INTO folders (id, show_in_app_list) VALUES ($FIRST_FOLDER_ID, 1)")
                db.execSQL("INSERT INTO folders (id, show_in_app_list) VALUES ($SECOND_FOLDER_ID, 0)")
            }
        ) { db ->
            MIGRATION_11_12.migrate(db)

            val actualColumns: List<String> = db.columnNames(table = "folders")
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

            val actualColumns: List<String> = db.columnNames(table = SETTINGS_TABLE)
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
                    "CREATE TABLE $FAVORITES_TABLE (key TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(key))"
                )
                db.execSQL("INSERT INTO $FAVORITES_TABLE (key, position) VALUES ('$FAVORITE_KEY', 0)")
            }
        ) { db ->
            MIGRATION_13_14.migrate(db)

            assertEquals(listOf("key", "added_at"), db.columnNames(table = STANDALONE_SHORTCUTS_TABLE))
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

            val actualColumns: List<String> = db.columnNames(table = SETTINGS_TABLE)
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
                db.execSQL("CREATE TABLE $LAUNCHABLE_CUSTOMIZATIONS_TABLE (key TEXT NOT NULL, name TEXT, PRIMARY KEY(key))")
                db.execSQL("INSERT INTO $LAUNCHABLE_CUSTOMIZATIONS_TABLE (key, name) VALUES ('$FAVORITE_KEY', 'Renamed')")
            }
        ) { db ->
            MIGRATION_16_17.migrate(db)

            assertEquals(listOf("key", "name", "icon"), db.columnNames(table = LAUNCHABLE_CUSTOMIZATIONS_TABLE))
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

            val actualColumns: List<String> = db.columnNames(table = SETTINGS_TABLE)
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
                db.columnNames(table = SETTINGS_TABLE)
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
                db.columnNames(table = SEARCH_HISTORY_TABLE)
            )
        }
    }

    @Test
    fun `adds the Search rows and engine defaulting on and Google and keeps the settings row`() {
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
                db.columnNames(table = SETTINGS_TABLE)
            )
            assertEquals(true, db.searchCalculatorRow())
            assertEquals(true, db.searchWebRow())
            assertEquals(true, db.searchPlayStoreRow())
            assertEquals(SearchEngine.Google.name, db.searchEngine())
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
                db.columnNames(table = SETTINGS_TABLE)
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
                db.columnNames(table = SETTINGS_TABLE)
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

            val actualColumns: List<String> = db.columnNames(table = SETTINGS_TABLE)
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
            SettingsColumn(Settings.COLUMN_SEARCH_PLAY_STORE_ROW, "INTEGER NOT NULL DEFAULT 1", value.toSql())
        },
        searchEngine?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_ENGINE, "TEXT NOT NULL DEFAULT '${SearchEngine.Google.name}'", "'${value.name}'")
        },
        searchEngineTemplate?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_ENGINE_TEMPLATE, "TEXT", "'$value'")
        },
        searchAutoLaunch?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_AUTO_LAUNCH, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        },
        searchBottomAnchoredResults?.let { value ->
            SettingsColumn(Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS, "INTEGER NOT NULL DEFAULT 0", value.toSql())
        }
    )
    val definitions: String = columns.joinToString(separator = "") { column -> ", ${column.name} ${column.definition}" }
    val columnNames: String = columns.joinToString(separator = "") { column -> ", ${column.name}" }
    val values: String = columns.joinToString(separator = "") { column -> ", ${column.value}" }
    execSQL("CREATE TABLE $SETTINGS_TABLE (id INTEGER NOT NULL, icon_pack TEXT$definitions, PRIMARY KEY(id))")
    execSQL("INSERT INTO $SETTINGS_TABLE (id, icon_pack$columnNames) VALUES (0, '$iconPack'$values)")
}

private fun Boolean.toSql(): String = if (this) "1" else "0"

private fun SupportSQLiteDatabase.columnNames(table: String): List<String> = query("PRAGMA table_info($table)").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            cursor.moveToNext()
        }
    }
}

private fun SupportSQLiteDatabase.favoriteKeys(): List<String> = query("SELECT key FROM $FAVORITES_TABLE ORDER BY position").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(cursor.getString(cursor.getColumnIndexOrThrow("key")))
            cursor.moveToNext()
        }
    }
}

private fun SupportSQLiteDatabase.folderIds(): List<Long> = query("SELECT id FROM folders ORDER BY id").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            cursor.moveToNext()
        }
    }
}

private fun SupportSQLiteDatabase.hidePrivateSpaceWhenLocked(): Boolean =
    query("SELECT ${Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED)) != 0
    }

private fun SupportSQLiteDatabase.scrubEdges(): String = query("SELECT ${Settings.COLUMN_SCRUB_EDGES} FROM $SETTINGS_TABLE").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SCRUB_EDGES))
}

private fun SupportSQLiteDatabase.railSide(): String = query("SELECT ${Settings.COLUMN_RAIL_SIDE} FROM $SETTINGS_TABLE").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_RAIL_SIDE))
}

private fun SupportSQLiteDatabase.openSearchOnSwipeUp(): Boolean =
    query("SELECT ${Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP)) != 0
    }

private fun SupportSQLiteDatabase.openSearchOnHomePress(): Boolean =
    query("SELECT ${Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS)) != 0
    }

private fun SupportSQLiteDatabase.searchSuggestions(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_SUGGESTIONS} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_SUGGESTIONS)) != 0
    }

private fun SupportSQLiteDatabase.searchAutoLaunch(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_AUTO_LAUNCH} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_AUTO_LAUNCH)) != 0
    }

private fun SupportSQLiteDatabase.searchBottomAnchoredResults(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS)) != 0
    }

private fun SupportSQLiteDatabase.searchCalculatorRow(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_CALCULATOR_ROW} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_CALCULATOR_ROW)) != 0
    }

private fun SupportSQLiteDatabase.searchWebRow(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_WEB_ROW} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_WEB_ROW)) != 0
    }

private fun SupportSQLiteDatabase.searchPlayStoreRow(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_PLAY_STORE_ROW} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_PLAY_STORE_ROW)) != 0
    }

private fun SupportSQLiteDatabase.searchContacts(): Boolean =
    query("SELECT ${Settings.COLUMN_SEARCH_CONTACTS} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_CONTACTS)) != 0
    }

private fun SupportSQLiteDatabase.searchEngine(): String =
    query("SELECT ${Settings.COLUMN_SEARCH_ENGINE} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_ENGINE))
    }

private fun SupportSQLiteDatabase.searchEngineTemplate(): String? =
    query("SELECT ${Settings.COLUMN_SEARCH_ENGINE_TEMPLATE} FROM $SETTINGS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow(Settings.COLUMN_SEARCH_ENGINE_TEMPLATE))
    }

private fun SupportSQLiteDatabase.iconPack(): String? = query("SELECT icon_pack FROM $SETTINGS_TABLE").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow("icon_pack"))
}

private fun SupportSQLiteDatabase.launchableCustomizationName(): String? =
    query("SELECT name FROM $LAUNCHABLE_CUSTOMIZATIONS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow("name"))
    }

private fun SupportSQLiteDatabase.launchableCustomizationIcon(): String? =
    query("SELECT icon FROM $LAUNCHABLE_CUSTOMIZATIONS_TABLE").use { cursor ->
        cursor.moveToFirst()
        cursor.getString(cursor.getColumnIndexOrThrow("icon"))
    }
