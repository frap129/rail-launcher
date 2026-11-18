package feature.launcher

import android.content.Context
import android.content.IntentSender
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import core.util.logTypeName
import timber.log.Timber

/**
 * Opens the Store page of [packageName] in [profile], returning whether the system accepted it.
 * Only the system's own market activity intent opens the Store in another Profile, and only
 * Android 15, where Private Space exists, offers one.
 */
internal fun Context.openStorePage(packageName: String, profile: UserHandle): Boolean =
    marketActivityIntent(packageName = packageName, profile = profile)?.let { intentSender ->
        tryStartIntentSender(intentSender = intentSender)
    } ?: false

/** The system's market activity intent for [packageName] in [profile], or null when it offers none. */
private fun Context.marketActivityIntent(packageName: String, profile: UserHandle): IntentSender? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return null
    }
    val launcherApps: LauncherApps = applicationContext.getSystemService(LauncherApps::class.java)
    return runCatching {
        launcherApps.getAppMarketActivityIntent(packageName, profile)
    }.getOrElse { exception ->
        Timber.w("Could not build the Store page intent: %s", exception.logTypeName())
        null
    }
}
