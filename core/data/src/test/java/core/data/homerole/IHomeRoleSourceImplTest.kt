package core.data.homerole

import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import core.data.testing.TEST_SDK
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IHomeRoleSourceImplTest {
    private val mockApplication: Application = mockk(relaxed = true)
    private val mockRoleManager: RoleManager = mockk(relaxed = true)

    @Test
    fun `reports that Rail holds the Home role`() {
        every { mockRoleManager.isRoleHeld(RoleManager.ROLE_HOME) } returns true
        val homeRoleSource: IHomeRoleSource = createSource()
        assertTrue(homeRoleSource.isHomeRoleHeld.value)
    }

    @Test
    fun `reports that Rail does not hold the Home role`() {
        every { mockRoleManager.isRoleHeld(RoleManager.ROLE_HOME) } returns false
        val homeRoleSource: IHomeRoleSource = createSource()
        assertFalse(homeRoleSource.isHomeRoleHeld.value)
    }

    @Test
    fun `refreshes the Home role from RoleManager`() {
        every { mockRoleManager.isRoleHeld(RoleManager.ROLE_HOME) } returnsMany listOf(false, true)
        val homeRoleSource: IHomeRoleSource = createSource()
        assertFalse(homeRoleSource.isHomeRoleHeld.value)
        homeRoleSource.refreshHomeRole()
        assertTrue(homeRoleSource.isHomeRoleHeld.value)
        verify(exactly = 2) { mockRoleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    }

    @Test
    fun `builds the system Home role request`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW)
        every { mockRoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME) } returns inputIntent
        val homeRoleSource: IHomeRoleSource = createSource()
        val actualIntent: Intent? = homeRoleSource.createHomeRoleRequestIntent()
        assertSame(inputIntent, actualIntent)
        verify(exactly = 1) { mockRoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME) }
    }

    @Test
    fun `reports no Home role and no request when RoleManager is unavailable`() {
        val homeRoleSource: IHomeRoleSource = createSource(roleManager = null)
        assertFalse(homeRoleSource.isHomeRoleHeld.value)
        assertNull(homeRoleSource.createHomeRoleRequestIntent())
    }

    private fun createSource(roleManager: RoleManager? = mockRoleManager): IHomeRoleSource {
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.getSystemService(RoleManager::class.java) } returns roleManager
        return IHomeRoleSourceImpl(context = mockApplication)
    }
}
