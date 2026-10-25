package core.data.popups

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** A Launchable the user added to one App's Pop-up, at [position] in the user's order. */
@Entity(
    tableName = "popup_items",
    primaryKeys = ["owner_key", "item_key"],
    indices = [Index(value = ["owner_key"])]
)
data class PopupItemRecord(
    @ColumnInfo(name = "owner_key") val ownerKey: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    val position: Int
)
