package core.data.popups

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Rail's Pop-up preferences, held in a single row. */
@Entity(tableName = "popup_settings")
data class PopupSettingsRecord(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "notifications_enabled", defaultValue = "0") val notificationsEnabled: Boolean = false
)
