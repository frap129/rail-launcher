package core.util

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.IntentSender.SendIntentException
import android.os.Build
import timber.log.Timber

/** The fill-in Intent for an IntentSender launch that supplies none. */
internal val INTENT_SENDER_FILL_IN_INTENT: Intent? = null

/** The mask of flags the IntentSender launch sets. */
internal const val INTENT_SENDER_FLAGS_MASK: Int = 0

/** The values the IntentSender launch sets for the masked flags. */
internal const val INTENT_SENDER_FLAG_VALUES: Int = 0

/** The extra flags the IntentSender launch sets. */
internal const val INTENT_SENDER_EXTRA_FLAGS: Int = 0

/**
 * Runs [launch], returning whether the platform accepted it. The failures the platform reports
 * for a target that is gone, a sender that can no longer be sent or an intent it cannot handle
 * are logged and reported as false; any other exception is rethrown.
 */
fun attemptLaunch(launch: () -> Unit): Boolean = runCatching { launch() }.fold(
    onSuccess = { true },
    onFailure = { exception ->
        if (exception is ActivityNotFoundException ||
            exception is SecurityException ||
            exception is IllegalStateException ||
            exception is SendIntentException
        ) {
            Timber.w("Could not open the target: %s", exception.logTypeName())
            false
        } else {
            throw exception
        }
    }
)

/** Starts [intent], returning whether an app accepted it. */
fun Context.tryStartActivity(intent: Intent): Boolean = attemptLaunch { startActivity(intent) }

/**
 * Activity options for opening a system screen or store page Rail reaches through one of the
 * system's IntentSenders, which only Android 16 offers.
 * [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE] is the least permissive mode
 * that still lets the visible launcher start the screen behind the system's IntentSender.
 */
internal fun privateSpaceLaunchOptions(): ActivityOptions? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
} else {
    null
}

/** Starts the system's [intentSender], returning whether the system accepted it. */
fun Context.tryStartIntentSender(intentSender: IntentSender): Boolean = attemptLaunch {
    startIntentSender(
        intentSender,
        INTENT_SENDER_FILL_IN_INTENT,
        INTENT_SENDER_FLAGS_MASK,
        INTENT_SENDER_FLAG_VALUES,
        INTENT_SENDER_EXTRA_FLAGS,
        privateSpaceLaunchOptions()?.toBundle()
    )
}
