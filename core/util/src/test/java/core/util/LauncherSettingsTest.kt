package core.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import core.data.testing.RAIL_PACKAGE_NAME
import core.data.testing.TEST_SDK
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LauncherSettingsTest {
    private val inputProcessId: Int = 42
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { mockContext.packageName } returns RAIL_PACKAGE_NAME
    }

    @Test
    fun `opens the system Default Home settings`() {
        val actualIntents: MutableList<Intent> = mutableListOf()
        every { mockContext.startActivity(any()) } answers { actualIntents += firstArg<Intent>() }
        mockContext.openDefaultHomeSettings()
        assertEquals(Settings.ACTION_HOME_SETTINGS, actualIntents.single().action)
    }

    @Test
    fun `restarts Rail through a fresh HOME task before ending its process`() {
        val actualIntents: MutableList<Intent> = mutableListOf()
        every { mockContext.startActivity(any()) } answers { actualIntents += firstArg<Intent>() }
        withMockedProcess {
            mockContext.restartRail()
            verifyOrder {
                mockContext.startActivity(any())
                Process.killProcess(inputProcessId)
            }
        }
        val actualIntent: Intent = actualIntents.single()
        assertEquals(Intent.ACTION_MAIN, actualIntent.action)
        assertTrue(actualIntent.hasCategory(Intent.CATEGORY_HOME))
        assertEquals(RAIL_PACKAGE_NAME, actualIntent.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, actualIntent.flags)
    }

    @Test
    fun `keeps Rail running when the HOME relaunch is rejected`() {
        every { mockContext.startActivity(any()) } throws SecurityException("Could not start HOME")
        withMockedProcess {
            mockContext.restartRail()
            verify(exactly = 0) { Process.killProcess(any()) }
        }
    }

    private fun withMockedProcess(block: () -> Unit) {
        mockkStatic(Process::class)
        every { Process.myPid() } returns inputProcessId
        every { Process.killProcess(inputProcessId) } just Runs
        try {
            block()
        } finally {
            unmockkStatic(Process::class)
        }
    }
}
