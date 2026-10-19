package core.data.prefs

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 0, val iconPack: String? = null)
