package core.data.apps

import android.content.ComponentName
import androidx.room.Entity
import core.data.apps.model.App
import core.data.apps.model.AppFields
import core.data.profiles.ProfileInfo

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

/** This App as a stored Last-known App list entry. */
internal fun App.toRecord(): LastKnownAppRecord = LastKnownAppRecord(
    componentName = componentName,
    profileSerial = profileInfo.serial,
    label = defaultName,
    badgedLabel = badgedLabel,
    isPackageRepresentative = isPackageRepresentative,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isRemovable = isRemovable,
    isSuspended = isSuspended
)

/** The App this stored entry describes for [profileInfo], the Profile its serial resolved to. */
internal fun LastKnownAppRecord.toApp(profileInfo: ProfileInfo): App = AppFields(
    defaultName = label,
    profileInfo = profileInfo,
    componentName = componentName,
    badgedLabel = badgedLabel,
    isPackageRepresentative = isPackageRepresentative,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isRemovable = isRemovable,
    isSuspended = isSuspended
).toApp()
