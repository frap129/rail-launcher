package core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Runs [launch], returning whether the platform accepted it. The failures the platform reports
 * for a target that is gone or cannot handle the intent are logged and reported as false; any
 * other exception is rethrown.
 */
fun attemptLaunch(launch: () -> Unit): Boolean = runCatching { launch() }.fold(
    onSuccess = { true },
    onFailure = { exception ->
        if (exception is ActivityNotFoundException || exception is SecurityException || exception is IllegalStateException) {
            Timber.w(exception, "Could not open the target")
            false
        } else {
            throw exception
        }
    }
)

/** Starts [intent], returning whether an app accepted it. */
fun Context.tryStartActivity(intent: Intent): Boolean = attemptLaunch { startActivity(intent) }
