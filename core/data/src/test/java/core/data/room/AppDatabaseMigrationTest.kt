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
private const val FIRST_FOLDER_ID: Long = 1L
private const val SECOND_FOLDER_ID: Long = 2L
private const val SETTINGS_TABLE: String = "settings"

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
                db.execSQL("CREATE TABLE settings (id INTEGER NOT NULL, icon_pack TEXT, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO settings (id, icon_pack) VALUES (0, '$TEST_ICON_PACK_PACKAGE')")
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

private fun SupportSQLiteDatabase.columnNames(table: String): List<String> = query("PRAGMA table_info($table)").use { cursor ->
    cursor.moveToFirst()
    buildList {
        while (!cursor.isAfterLast) {
            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
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

private fun SupportSQLiteDatabase.iconPack(): String? = query("SELECT icon_pack FROM $SETTINGS_TABLE").use { cursor ->
    cursor.moveToFirst()
    cursor.getString(cursor.getColumnIndexOrThrow("icon_pack"))
}
