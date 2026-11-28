package core.data.homerole

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** Reports whether Rail holds the system Home role and builds the system's Home role request. */
interface IHomeRoleSource {
    val isHomeRoleHeld: StateFlow<Boolean>

    /** Rechecks the Home role after the hosting Activity resumes. */
    fun refreshHomeRole()

    /** The Intent that opens the system Home role dialog, or null when the platform cannot offer it. */
    fun createHomeRoleRequestIntent(): Intent?
}
