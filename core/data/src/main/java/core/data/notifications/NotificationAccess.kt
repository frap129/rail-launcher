package core.data.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** Whether Rail's notification listener is enabled in system settings. */
fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
