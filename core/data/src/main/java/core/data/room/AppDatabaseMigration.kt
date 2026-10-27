package core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val FOLDERS_TABLE: String = "folders"
private const val REBUILT_FOLDERS_TABLE: String = "folders_rebuilt"

/** Removes the retired App List placement flag while keeping every Folder. */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE $REBUILT_FOLDERS_TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("INSERT INTO $REBUILT_FOLDERS_TABLE (id) SELECT id FROM $FOLDERS_TABLE")
        db.execSQL("DROP TABLE $FOLDERS_TABLE")
        db.execSQL("ALTER TABLE $REBUILT_FOLDERS_TABLE RENAME TO $FOLDERS_TABLE")
    }
}
