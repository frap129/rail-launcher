package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 0, @ColumnInfo(name = Settings.COLUMN_ICON_PACK) val iconPack: String? = null) {
    companion object {
        const val COLUMN_ICON_PACK: String = "icon_pack"
    }
}
