package core.data.homerole

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class IHomeRoleSourceImpl(context: Context) : IHomeRoleSource {
    private val appContext: Context = context.applicationContext
    private val roleManager: RoleManager? = appContext.getSystemService(RoleManager::class.java)
    private val mutableIsHomeRoleHeld: MutableStateFlow<Boolean> = MutableStateFlow(readIsHomeRoleHeld())
    override val isHomeRoleHeld: StateFlow<Boolean> = mutableIsHomeRoleHeld.asStateFlow()

    override fun refreshHomeRole() {
        mutableIsHomeRoleHeld.value = readIsHomeRoleHeld()
    }

    override fun createHomeRoleRequestIntent(): Intent? = roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)

    private fun readIsHomeRoleHeld(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: run {
        Timber.w("RoleManager is unavailable; treating Rail as not holding the Home role")
        false
    }
}
