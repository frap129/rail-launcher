package feature.launcher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createPrivateApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherLaunchPathTest {
    private val mockContext: Context = mockk(relaxed = true)
    private val mockViewModel: LauncherViewModel = mockk(relaxed = true)
    private val mockLaunchable: Launchable = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { mockLaunchable.isDisabled } returns false
        every { mockLaunchable.launch(any()) } returns Unit
        every { mockViewModel.requestLaunch(mockLaunchable) } returns true
    }

    @Test
    fun `a launch reaches onLaunched`() {
        val actualLaunched: Boolean = launch(launchable = mockLaunchable)

        assertTrue(actualLaunched)
        verify { mockViewModel.onLaunched(mockLaunchable) }
    }

    @Test
    fun `a disabled Launchable does not launch`() {
        every { mockLaunchable.isDisabled } returns true

        val actualLaunched: Boolean = launch(launchable = mockLaunchable)

        assertNotLaunched(actualLaunched = actualLaunched)
    }

    @Test
    fun `a declined Paused prompt does not launch`() {
        every { mockViewModel.requestLaunch(mockLaunchable) } returns false

        val actualLaunched: Boolean = launch(launchable = mockLaunchable)

        assertNotLaunched(actualLaunched = actualLaunched)
    }

    @Test
    fun `a failed launch does not reach onLaunched`() {
        every { mockLaunchable.launch(any()) } throws ActivityNotFoundException()
        var actualFailed: Launchable? = null

        val actualLaunched: Boolean = launch(
            launchable = mockLaunchable,
            effects = effects(onLaunchFailed = { launchable -> actualFailed = launchable })
        )

        assertFalse(actualLaunched)
        assertEquals(mockLaunchable, actualFailed)
        verify(exactly = 0) { mockViewModel.onLaunched(any()) }
    }

    @Test
    fun `a private Launchable launch failure logs without its name, package or key`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPrivateApp: App = createPrivateApp()
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every {
            mockLauncherApps.startMainActivity(inputPrivateApp.componentName, inputPrivateApp.profile, null, null)
        } throws SecurityException("Could not open ${inputPrivateApp.key}")
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        every { mockViewModel.requestLaunch(inputPrivateApp) } returns true
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record {
            launch(launchable = inputPrivateApp, context = inputContext)
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.name, inputPrivateApp.packageName, inputPrivateApp.key)
    }

    private fun launch(launchable: Launchable, context: Context = mockContext, effects: LaunchItemEffects = effects()): Boolean =
        launchLauncherItem(launchable = launchable, context = context, viewModel = mockViewModel, effects = effects)

    /** Asserts a launch that did not happen neither opened nor reached onLaunched. */
    private fun assertNotLaunched(actualLaunched: Boolean) {
        assertFalse(actualLaunched)
        verify(exactly = 0) { mockLaunchable.launch(any()) }
        verify(exactly = 0) { mockViewModel.onLaunched(any()) }
    }

    private fun effects(onLaunchFailed: (Launchable) -> Unit = {}): LaunchItemEffects = LaunchItemEffects(
        onHideKeyboard = {},
        onDisabledLaunch = {},
        onPopupLaunched = {},
        onLaunchFailed = onLaunchFailed,
        onHomeIntent = {}
    )
}
