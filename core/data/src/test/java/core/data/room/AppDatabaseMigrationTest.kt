package core.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import core.data.prefs.Settings
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
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
private const val FIRST_FOLDER_ID: Long = 1L
private const val SECOND_FOLDER_ID: Long = 2L
private const val SETTINGS_TABLE: String = "settings"
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

/** Creates a settings table and inserts one row, optionally with the Private Space switch column. */
private fun SupportSQLiteDatabase.createSettingsRow(iconPack: String, hidePrivateSpaceWhenLocked: Boolean? = null) {
    val hideColumnDefinition: String = if (hidePrivateSpaceWhenLocked == null) {
        ""
    } else {
        ", ${Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED} INTEGER NOT NULL DEFAULT 0"
    }
    val hideColumn: String = if (hidePrivateSpaceWhenLocked == null) "" else ", ${Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED}"
    val hideValue: String = if (hidePrivateSpaceWhenLocked == null) "" else ", ${if (hidePrivateSpaceWhenLocked) 1 else 0}"
    execSQL("CREATE TABLE $SETTINGS_TABLE (id INTEGER NOT NULL, icon_pack TEXT$hideColumnDefinition, PRIMARY KEY(id))")
    execSQL("INSERT INTO $SETTINGS_TABLE (id, icon_pack$hideColumn) VALUES (0, '$iconPack'$hideValue)")
}

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

private fun SupportSQLiteDatabase.iconPack(): String? = query("SELECT icon_pack FROM $SETTINGS_TABLE").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow("icon_pack"))
}
