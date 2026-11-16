package core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import timber.log.Timber

/** Opens the system uninstall confirmation for [packageName] in [profile]. */
fun Context.requestUninstall(packageName: String, profile: UserHandle) {
    val intent: Intent = Intent(
        Intent.ACTION_DELETE,
        Uri.fromParts("package", packageName, null)
    )
        .putExtra(Intent.EXTRA_USER, profile)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        startActivity(intent)
    }.onFailure { exception ->
        Timber.w("Could not request an uninstall for %s: %s", profile, exception.logTypeName())
    }
}
