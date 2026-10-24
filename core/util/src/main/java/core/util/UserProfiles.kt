package core.util

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import timber.log.Timber

/** The user profile whose serial number is [serial], or null when no profile matches. */
fun Context.userForSerial(serial: Long): UserHandle? {
    val userManager: UserManager = getSystemService(UserManager::class.java)
    return userManager.userProfiles.firstOrNull { user -> userManager.getSerialNumberForUser(user) == serial }
}

/** Asks the system to leave quiet mode for [profile]. */
fun Context.requestQuietModeOff(profile: UserHandle) {
    val userManager: UserManager = getSystemService(UserManager::class.java)
    runCatching { userManager.requestQuietModeEnabled(false, profile) }
        .onFailure { exception -> Timber.w(exception, "Could not leave quiet mode for %s", profile) }
}
