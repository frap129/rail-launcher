package core.data.widgets

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Puts the default Header in the Widget Area. Runs on a fresh database and after a
 * destructive migration, so a wiped database never starts with an empty Widget Area.
 */
class WidgetAreaSeedCallback : RoomDatabase.Callback() {
    private val isSeedPending: AtomicBoolean = AtomicBoolean(false)

    override fun onCreate(db: SupportSQLiteDatabase) {
        seed(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        // Tables are recreated after this callback, so defer the insert until onOpen.
        isSeedPending.set(true)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (isSeedPending.compareAndSet(true, false)) {
            seed(db)
        }
    }
}

private fun seed(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT INTO widget_places (position, height_dp, current_page) VALUES (0, NULL, 0)")
    db.execSQL(
        "INSERT INTO widgets " +
            "(place_id, page_position, kind, app_widget_id, provider, profile_serial, extends_under_rail) " +
            "VALUES (last_insert_rowid(), 0, 'HEADER', NULL, NULL, NULL, 0)"
    )
}
