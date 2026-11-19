package core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

private const val REBUILT_FOLDERS_TABLE: String = "folders_rebuilt"

/** Removes the retired App List placement flag while keeping every Folder. */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE $REBUILT_FOLDERS_TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("INSERT INTO $REBUILT_FOLDERS_TABLE (id) SELECT id FROM ${FolderRecord.TABLE_NAME}")
        db.execSQL("DROP TABLE ${FolderRecord.TABLE_NAME}")
        db.execSQL("ALTER TABLE $REBUILT_FOLDERS_TABLE RENAME TO ${FolderRecord.TABLE_NAME}")
    }
}

/** Adds the switch that hides a Locked Private Space, leaving every other setting in place. */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED, isEnabledByDefault = false)
    }
}

/** Adds the table that stores Standalone shortcut keys and when each was added. */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ${StandaloneShortcutRecord.TABLE_NAME} " +
                "(key TEXT NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(key))"
        )
    }
}

/** Adds the Scrub edges setting, defaulting to both edges, and keeps every other setting. */
val MIGRATION_14_15: Migration = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN ${Settings.COLUMN_SCRUB_EDGES} TEXT NOT NULL DEFAULT '${ScrubEdges.DEFAULT.name}'"
        )
    }
}

/** Adds the Rail side setting, defaulting to the layout end, and keeps every other setting. */
val MIGRATION_15_16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN ${Settings.COLUMN_RAIL_SIDE} TEXT NOT NULL DEFAULT '${RailSide.DEFAULT.name}'"
        )
    }
}

/** Adds the nullable Custom icon to every stored Launchable customization. */
val MIGRATION_16_17: Migration = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ${LaunchableCustomization.TABLE_NAME} ADD COLUMN icon TEXT")
    }
}

/**
 * Adds the two Search entry switches, defaulting to a swipe up opening Search and a Home press
 * not, and keeps every other setting.
 */
val MIGRATION_17_18: Migration = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_OPEN_SEARCH_ON_SWIPE_UP, isEnabledByDefault = true)
        db.addBooleanSetting(column = Settings.COLUMN_OPEN_SEARCH_ON_HOME_PRESS, isEnabledByDefault = false)
    }
}

/**
 * Adds the Search suggestions switch, defaulting to on, and the table that stores Search history,
 * keeping every other setting.
 */
val MIGRATION_18_19: Migration = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_SUGGESTIONS, isEnabledByDefault = true)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ${SearchHistoryRecord.TABLE_NAME} " +
                "(key TEXT NOT NULL, launch_count INTEGER NOT NULL, last_launched_at INTEGER NOT NULL, " +
                "removed INTEGER NOT NULL, PRIMARY KEY(key))"
        )
    }
}

/**
 * Adds the Calculator and web row switches, defaulting to on, the Play Store row switch,
 * defaulting to off, and the Search engine choice, defaulting to no engine, keeping every other
 * setting.
 */
val MIGRATION_19_20: Migration = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_CALCULATOR_ROW, isEnabledByDefault = true)
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_WEB_ROW, isEnabledByDefault = true)
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_PLAY_STORE_ROW, isEnabledByDefault = false)
        db.execSQL(
            "ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN ${Settings.COLUMN_SEARCH_ENGINE} TEXT NOT NULL " +
                "DEFAULT '${SearchEngine.None.name}'"
        )
        db.execSQL("ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN ${Settings.COLUMN_SEARCH_ENGINE_TEMPLATE} TEXT")
    }
}

/**
 * Adds the Auto-launch and Bottom-anchored results switches, both defaulting to off, and keeps
 * every other setting.
 */
val MIGRATION_20_21: Migration = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_AUTO_LAUNCH, isEnabledByDefault = false)
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_BOTTOM_ANCHORED_RESULTS, isEnabledByDefault = false)
    }
}

/**
 * Adds the Contact results switch, defaulting to off so Search never asks for the Contacts
 * permission on its own, and keeps every other setting.
 */
val MIGRATION_21_22: Migration = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_CONTACTS, isEnabledByDefault = false)
    }
}

/**
 * Adds the Live web suggestions switch, defaulting to off so Search never contacts the Search
 * engine on its own, and keeps every other setting.
 */
val MIGRATION_22_23: Migration = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_WEB_SUGGESTIONS, isEnabledByDefault = false)
    }
}

/**
 * Adds the Swipe down on Home switch, defaulting to on, and keeps every other setting.
 */
val MIGRATION_23_24: Migration = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_EXPAND_NOTIFICATIONS_ON_SWIPE_DOWN, isEnabledByDefault = true)
    }
}

/**
 * Adds the table that stores Launch counts and the App List order setting, defaulting to Recently
 * used. Adds the Rank Folders by use setting, defaulting to on so Folders rank like the other
 * Launchables, and keeps every other setting.
 */
val MIGRATION_24_25: Migration = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ${LaunchCountRecord.TABLE_NAME} " +
                "(key TEXT NOT NULL, launch_count INTEGER NOT NULL, recent_use_score REAL NOT NULL, " +
                "score_updated_at INTEGER NOT NULL, PRIMARY KEY(key))"
        )
        db.execSQL(
            "ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN ${Settings.COLUMN_APP_LIST_ORDER} TEXT NOT NULL " +
                "DEFAULT '${AppListOrder.DEFAULT.name}'"
        )
        db.addBooleanSetting(column = Settings.COLUMN_RANK_FOLDERS_BY_USE, isEnabledByDefault = true)
    }
}

/**
 * Adds the Hidden flag to every stored Launchable customization, defaulting to not hidden, and the
 * Show hidden apps Search setting, defaulting to on so Hidden apps stay searchable, keeping every
 * custom name, Custom icon and other setting.
 */
val MIGRATION_25_26: Migration = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ${LaunchableCustomization.TABLE_NAME} ADD COLUMN ${LaunchableCustomization.COLUMN_HIDDEN} " +
                "INTEGER NOT NULL DEFAULT 0"
        )
        db.addBooleanSetting(column = Settings.COLUMN_SEARCH_SHOW_HIDDEN_APPS, isEnabledByDefault = true)
    }
}

/**
 * Adds the Show Recently installed setting, defaulting to off so the App List ends where the user
 * expects, and keeps every other setting.
 */
val MIGRATION_26_27: Migration = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SHOW_RECENTLY_INSTALLED, isEnabledByDefault = false)
    }
}

/**
 * Adds the Rail haptics setting, defaulting on so scrubbing keeps its tick haptics, and keeps
 * every other setting.
 */
val MIGRATION_27_28: Migration = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_RAIL_HAPTICS, isEnabledByDefault = true)
    }
}

/**
 * Adds the Show Rail setting, defaulting on so the Rail draws its marks, and keeps every other
 * setting.
 */
val MIGRATION_28_29: Migration = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SHOW_RAIL, isEnabledByDefault = true)
    }
}

/**
 * Adds the Suggest often-used Apps setting, defaulting on so Home shows its suggestions, and keeps
 * every other setting.
 */
val MIGRATION_29_30: Migration = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addBooleanSetting(column = Settings.COLUMN_SUGGEST_OFTEN_USED_APPS, isEnabledByDefault = true)
    }
}

/** Every migration Rail applies, so [core.data.room.AppDatabase] can open any database Rail shipped. */
val APP_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30
)

/** Adds the Boolean settings [column], defaulting to [isEnabledByDefault], keeping every other setting. */
private fun SupportSQLiteDatabase.addBooleanSetting(column: String, isEnabledByDefault: Boolean) {
    val defaultValue: Int = if (isEnabledByDefault) 1 else 0
    execSQL("ALTER TABLE ${Settings.TABLE_NAME} ADD COLUMN $column INTEGER NOT NULL DEFAULT $defaultValue")
}
