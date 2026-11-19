package core.data.apps.model

import android.content.ComponentName
import core.data.profiles.ProfileInfo

/** The fields a live Profile activity and a Last-known App record both carry into an [App]. */
internal data class AppFields(
    val defaultName: String,
    val profileInfo: ProfileInfo,
    val componentName: ComponentName,
    val badgedLabel: String,
    val isPackageRepresentative: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isRemovable: Boolean,
    val isSystemApp: Boolean,
    val isSuspended: Boolean
) {
    fun toApp(): App = App(
        defaultName = defaultName,
        profileInfo = profileInfo,
        componentName = componentName,
        badgedLabel = badgedLabel,
        isPackageRepresentative = isPackageRepresentative,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        isRemovable = isRemovable,
        isSystemApp = isSystemApp,
        isSuspended = isSuspended
    )
}
