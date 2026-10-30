package core.data.icons

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import timber.log.Timber

/** The system-badged form of this drawable for [user], or null when the system cannot badge it. */
fun Drawable.badgedFor(packageManager: PackageManager, user: UserHandle): Drawable? =
    runCatching { packageManager.getUserBadgedIcon(this, user) }
        .onFailure { exception -> Timber.w(exception, "Could not badge a drawable for %s", user) }
        .getOrNull()
