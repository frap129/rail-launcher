package core.util

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

internal const val PACKAGE_INFO_FLAGS: Int = 0

/** Reads this package's version name, returning and logging [fallback] when it is unavailable. */
fun Context.readAppVersionName(fallback: String): String {
    val versionName: String? = try {
        packageManager.getPackageInfo(packageName, PACKAGE_INFO_FLAGS).versionName
    } catch (exception: PackageManager.NameNotFoundException) {
        Timber.w("Could not read Rail's version name: %s", exception.logTypeName())
        return fallback
    }
    if (versionName.isNullOrBlank()) {
        Timber.w("Rail's package has no version name")
        return fallback
    }
    return versionName
}
