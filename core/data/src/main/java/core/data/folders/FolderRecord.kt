package core.data.folders

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A stored Folder. Its name and icon are resolved through the Launchable customizations. */
@Entity(tableName = FolderRecord.TABLE_NAME)
data class FolderRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0L) {
    companion object {
        const val TABLE_NAME: String = "folders"
    }
}
