package core.data.folders

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A stored Folder. Its name and icon are resolved through the Launchable customizations. */
@Entity(tableName = "folders")
data class FolderRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "show_in_app_list") val showInAppList: Boolean = false
)
