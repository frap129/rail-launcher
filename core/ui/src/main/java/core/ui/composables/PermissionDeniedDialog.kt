package core.ui.composables

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import core.ui.R
import core.util.openApplicationDetailsSettings

/** Explains that [title] needs a permission, offering Rail's app settings to grant it. */
@Composable
fun PermissionDeniedDialog(title: String, message: String, onDismiss: () -> Unit) {
    val context: Context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.openApplicationDetailsSettings()
                }
            ) {
                Text(text = stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
