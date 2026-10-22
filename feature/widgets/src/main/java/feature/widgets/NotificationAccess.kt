package feature.widgets

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import core.data.media.RailNotificationListener
import core.util.openApplicationDetailsSettings
import timber.log.Timber

/** Opens the system's notification-access page for Rail's listener. */
fun Context.openRailNotificationListenerSettings() {
    val component: ComponentName = ComponentName(this, RailNotificationListener::class.java)
    val detailIntent: Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    try {
        startActivity(detailIntent)
    } catch (exception: ActivityNotFoundException) {
        Timber.w(exception, "No notification listener detail screen")
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (exception: SecurityException) {
        Timber.w(exception, "No permission to open notification listener settings")
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

/** True when no installer of record owns [packageName], which hides restricted settings. */
fun PackageManager.isSideloaded(packageName: String): Boolean = try {
    getInstallSourceInfo(packageName).installingPackageName == null
} catch (exception: PackageManager.NameNotFoundException) {
    false
} catch (exception: SecurityException) {
    false
}

/** Explains notification access before sending the user to system settings. */
@Composable
fun NotificationAccessDialog(onDismiss: () -> Unit) {
    val context: Context = LocalContext.current
    val isSideloaded: Boolean = remember(context) { context.packageManager.isSideloaded(packageName = context.packageName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.allow_notification_access_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.allow_notification_access_body))
                if (isSideloaded) {
                    Text(
                        text = stringResource(R.string.allow_notification_access_sideloaded),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (isSideloaded) {
                    TextButton(
                        onClick = {
                            context.openApplicationDetailsSettings()
                            onDismiss()
                        }
                    ) {
                        Text(text = stringResource(R.string.app_info))
                    }
                }
                TextButton(
                    onClick = {
                        context.openRailNotificationListenerSettings()
                        onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.open_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.not_now))
            }
        }
    )
}
