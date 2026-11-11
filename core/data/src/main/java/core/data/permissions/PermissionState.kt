package core.data.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The runtime state of one permission, rechecked on demand. */
class PermissionState(context: Context, permission: String) {
    private val appContext: Context = context.applicationContext
    private val permissionName: String = permission
    private val granted: MutableStateFlow<Boolean> = MutableStateFlow(isGranted())

    val value: StateFlow<Boolean> = granted.asStateFlow()

    /** Rechecks [permissionName], so the state follows a change made outside Rail. */
    fun refresh() {
        granted.value = isGranted()
    }

    private fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        permissionName
    ) == PackageManager.PERMISSION_GRANTED
}
