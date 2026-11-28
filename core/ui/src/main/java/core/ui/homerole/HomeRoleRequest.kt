package core.ui.homerole

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import core.data.homerole.IHomeRoleSource
import core.util.tryStartActivity
import timber.log.Timber

/** Remembers the shared action that starts the system Home role request. */
@Composable
fun rememberHomeRoleRequest(homeRoleSource: IHomeRoleSource): () -> Unit {
    val context: Context = LocalContext.current
    return remember(context, homeRoleSource) {
        { startHomeRoleRequest(context = context, homeRoleSource = homeRoleSource) }
    }
}

/** Starts the system Home role request, logging when the platform cannot offer it. */
internal fun startHomeRoleRequest(context: Context, homeRoleSource: IHomeRoleSource) {
    val homeRoleRequest: Intent? = homeRoleSource.createHomeRoleRequestIntent()
    if (homeRoleRequest == null) {
        Timber.w("The system offers no Home role request Intent")
        return
    }
    context.tryStartActivity(homeRoleRequest)
}
