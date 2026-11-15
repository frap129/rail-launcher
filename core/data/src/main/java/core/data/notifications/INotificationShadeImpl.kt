package core.data.notifications

import android.annotation.SuppressLint
import android.content.Context
import timber.log.Timber

// The public Context constant needs API 33, above Rail's minimum SDK.
private const val STATUS_BAR_SERVICE: String = "statusbar"
private const val EXPAND_NOTIFICATIONS_PANEL: String = "expandNotificationsPanel"

class INotificationShadeImpl(private val context: Context) : INotificationShade {
    // StatusBarManager offers no public way to expand the shade, so its unsupported method is called by name.
    @SuppressLint("WrongConstant")
    override fun expand() {
        val statusBarManager: Any = context.getSystemService(STATUS_BAR_SERVICE) ?: run {
            Timber.w("No status bar service to expand the notification shade")
            return
        }
        try {
            statusBarManager.javaClass.getMethod(EXPAND_NOTIFICATIONS_PANEL).invoke(statusBarManager)
        } catch (err: ReflectiveOperationException) {
            Timber.w(err, "Couldn't expand the notification shade")
        }
    }
}
