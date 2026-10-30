package core.data.testing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender

private const val INTENT_REQUEST_CODE: Int = 0

/** A real [IntentSender] for tests that open a system screen, built from a [PendingIntent]. */
fun createIntentSender(context: Context): IntentSender = PendingIntent.getActivity(
    context,
    INTENT_REQUEST_CODE,
    Intent(),
    PendingIntent.FLAG_IMMUTABLE
).intentSender
