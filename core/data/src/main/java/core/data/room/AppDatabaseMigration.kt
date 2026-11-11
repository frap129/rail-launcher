package core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings

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

private const val SETTINGS_TABLE: String = "settings"

/** Adds the switch that hides a Locked Private Space, leaving every other setting in place. */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED} INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private const val STANDALONE_SHORTCUTS_TABLE: String = "standalone_shortcuts"

/** Adds the table that stores Standalone shortcut keys and when each was added. */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $STANDALONE_SHORTCUTS_TABLE " +
                "(key TEXT NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(key))"
        )
    }
}

/** Adds the Scrub edges setting, defaulting to both edges, and keeps every other setting. */
val MIGRATION_14_15: Migration = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SCRUB_EDGES} TEXT NOT NULL DEFAULT '${ScrubEdges.DEFAULT.name}'"
        )
    }
}

/** Adds the Rail side setting, defaulting to the layout end, and keeps every other setting. */
val MIGRATION_15_16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_RAIL_SIDE} TEXT NOT NULL DEFAULT '${RailSide.DEFAULT.name}'"
        )
    }
}

private const val LAUNCHABLE_CUSTOMIZATIONS_TABLE: String = "launchable_customizations"

/** Adds the nullable Custom icon to every stored Launchable customization. */
val MIGRATION_16_17: Migration = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $LAUNCHABLE_CUSTOMIZATIONS_TABLE ADD COLUMN icon TEXT")
    }
}

/**
 * Adds the two Search entry switches, defaulting to a swipe up opening Search and a Home press
 * not, and keeps every other setting.
 */
val MIGRATION_17_18: Migration = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP} INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS} INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private const val SEARCH_HISTORY_TABLE: String = "search_history"

/**
 * Adds the Search suggestions switch, defaulting to on, and the table that stores Search history,
 * keeping every other setting.
 */
val MIGRATION_18_19: Migration = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_SUGGESTIONS} INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $SEARCH_HISTORY_TABLE " +
                "(key TEXT NOT NULL, launch_count INTEGER NOT NULL, last_launched_at INTEGER NOT NULL, " +
                "removed INTEGER NOT NULL, PRIMARY KEY(key))"
        )
    }
}

/**
 * Adds the Calculator, web and Play Store row switches, all defaulting to on, and the Search
 * engine choice, defaulting to Google with no custom template, keeping every other setting.
 */
val MIGRATION_19_20: Migration = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_CALCULATOR_ROW} INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_WEB_ROW} INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_PLAY_STORE_ROW} INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_ENGINE} TEXT NOT NULL " +
                "DEFAULT '${SearchEngine.Google.name}'"
        )
        db.execSQL("ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_ENGINE_TEMPLATE} TEXT")
    }
}

/**
 * Adds the Auto-launch and Bottom-anchored results switches, both defaulting to off, and keeps
 * every other setting.
 */
val MIGRATION_20_21: Migration = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_AUTO_LAUNCH} INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE $SETTINGS_TABLE ADD COLUMN ${Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS} INTEGER NOT NULL DEFAULT 0"
        )
    }
}
