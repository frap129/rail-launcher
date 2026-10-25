package core.data.popups

import androidx.room.ColumnInfo
import androidx.room.Entity

/** A shortcut the user hid from one App's Pop-up. */
@Entity(tableName = "popup_hidden_shortcuts", primaryKeys = ["owner_key", "shortcut_id"])
data class PopupHiddenShortcut(
    @ColumnInfo(name = "owner_key") val ownerKey: String,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String
)
