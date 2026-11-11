package core.ui.composables

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Launches the system permission request for [permission], reporting whether it was granted and
 * whether the system will still let Rail ask again.
 */
@Composable
fun rememberPermissionResultLauncher(
    permission: String,
    onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    val context: Context = LocalContext.current
    val activity: Activity? = remember(context) { context.findActivity() }
    val currentOnResult: (Boolean, Boolean) -> Unit by rememberUpdatedState(onResult)
    return rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted, activity?.shouldShowRequestPermissionRationale(permission) == true)
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}
