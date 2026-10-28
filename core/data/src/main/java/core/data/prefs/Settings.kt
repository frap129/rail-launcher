package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = Settings.COLUMN_ICON_PACK) val iconPack: String? = null,
    @ColumnInfo(name = Settings.COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED, defaultValue = "0")
    val hidePrivateSpaceWhenLocked: Boolean = false
) {
    companion object {
        const val COLUMN_ICON_PACK: String = "icon_pack"
        const val COLUMN_HIDE_PRIVATE_SPACE_WHEN_LOCKED: String = "hide_private_space_when_locked"
    }
}
