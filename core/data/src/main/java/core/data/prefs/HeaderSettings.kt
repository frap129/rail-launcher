package core.data.prefs

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "header_settings")
data class HeaderSettings(@PrimaryKey val id: Int = 0, val clockVisible: Boolean = true, val dateVisible: Boolean = true) {
    val canHideClock: Boolean get() = !clockVisible || dateVisible

    val canHideDate: Boolean get() = !dateVisible || clockVisible
}
