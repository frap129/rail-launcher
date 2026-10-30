package core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import timber.log.Timber

fun Context.openApplicationDetailsSettings(packageName: String) {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

/** Opens the app details of [packageName] in [profile], which may be a work profile. */
fun Context.openApplicationDetailsSettings(packageName: String, profile: UserHandle) {
    openApplicationDetailsSettings(componentName = ComponentName(packageName, ""), profile = profile)
}

/** Opens the app details of [componentName] in [profile], which may be a work profile. */
fun Context.openApplicationDetailsSettings(componentName: ComponentName, profile: UserHandle) {
    val launcherApps: LauncherApps = getSystemService(LauncherApps::class.java)
    runCatching {
        launcherApps.startAppDetailsActivity(componentName, profile, null, null)
    }.onFailure { exception ->
        Timber.w("Could not open app info for %s: %s", profile, exception.logTypeName())
    }
}

fun Context.openApplicationDetailsSettings() {
    openApplicationDetailsSettings(packageName = packageName)
}
