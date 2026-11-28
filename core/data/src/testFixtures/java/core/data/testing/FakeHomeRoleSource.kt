package core.data.testing

import android.content.Intent
import core.data.homerole.IHomeRoleSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeRoleSource(isHomeRoleHeld: Boolean = true) : IHomeRoleSource {
    private var currentIsHomeRoleHeld: Boolean = isHomeRoleHeld
    override val isHomeRoleHeld: MutableStateFlow<Boolean> = MutableStateFlow(isHomeRoleHeld)

    override fun createHomeRoleRequestIntent(): Intent? = null

    override fun refreshHomeRole() {
        isHomeRoleHeld.value = currentIsHomeRoleHeld
    }

    fun setHomeRoleHeld(isHeld: Boolean) {
        currentIsHomeRoleHeld = isHeld
        isHomeRoleHeld.value = isHeld
    }
}
