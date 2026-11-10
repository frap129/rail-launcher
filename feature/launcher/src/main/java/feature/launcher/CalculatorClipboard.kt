package feature.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import timber.log.Timber

/** Copies the Calculator row's [result] to the system clipboard, returning whether it was copied. */
internal fun copyCalculatorRowResult(context: Context, result: String): Boolean {
    val clipboard: ClipboardManager? = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        Timber.w("Clipboard service is unavailable")
        return false
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(null, result))
    return true
}
