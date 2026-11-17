package core.data.apps

import android.content.ComponentName
import androidx.room.Entity

/** A stored entry of the Last-known App list: one App as the last live load saw it. */
@Entity(tableName = LastKnownAppRecord.TABLE_NAME, primaryKeys = ["componentName", "profileSerial"])
data class LastKnownAppRecord(
    val componentName: ComponentName,
    val profileSerial: Long,
    val label: String,
    val badgedLabel: String,
    val isPackageRepresentative: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isRemovable: Boolean,
    val isSuspended: Boolean
) {
    companion object {
        const val TABLE_NAME: String = "last_known_apps"
    }
}
