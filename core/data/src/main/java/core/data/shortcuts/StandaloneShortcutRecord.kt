package core.data.shortcuts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Shortcut another app asked Rail to add, and when it was added. */
@Entity(tableName = StandaloneShortcutRecord.TABLE_NAME)
data class StandaloneShortcutRecord(@PrimaryKey val key: String, @ColumnInfo(name = "added_at") val addedAt: Long) {
    companion object {
        const val TABLE_NAME: String = "standalone_shortcuts"
    }
}
