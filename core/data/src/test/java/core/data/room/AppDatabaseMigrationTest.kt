package core.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val DATABASE_NAME: String = "folder-migration-test"
private const val OLD_DATABASE_VERSION: Int = 11
private const val FIRST_FOLDER_ID: Long = 1L
private const val SECOND_FOLDER_ID: Long = 2L

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppDatabaseMigrationTest {
    @Test
    fun `removes the App List flag and keeps every Folder`() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val callback: SupportSQLiteOpenHelper.Callback = object : SupportSQLiteOpenHelper.Callback(OLD_DATABASE_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, show_in_app_list INTEGER NOT NULL)")
                db.execSQL("INSERT INTO folders (id, show_in_app_list) VALUES ($FIRST_FOLDER_ID, 1)")
                db.execSQL("INSERT INTO folders (id, show_in_app_list) VALUES ($SECOND_FOLDER_ID, 0)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration: SupportSQLiteOpenHelper.Configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(callback)
            .build()
        val helper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database: SupportSQLiteDatabase = helper.writableDatabase

        MIGRATION_11_12.migrate(database)

        val actualColumns: List<String> = database.query("PRAGMA table_info(folders)").use { cursor ->
            cursor.moveToFirst()
            buildList {
                while (!cursor.isAfterLast) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    cursor.moveToNext()
                }
            }
        }
        val actualIds: List<Long> = database.query("SELECT id FROM folders ORDER BY id").use { cursor ->
            cursor.moveToFirst()
            buildList {
                while (!cursor.isAfterLast) {
                    add(cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                    cursor.moveToNext()
                }
            }
        }
        helper.close()
        context.deleteDatabase(DATABASE_NAME)

        assertEquals(listOf("id"), actualColumns)
        assertEquals(listOf(FIRST_FOLDER_ID, SECOND_FOLDER_ID), actualIds)
    }
}
