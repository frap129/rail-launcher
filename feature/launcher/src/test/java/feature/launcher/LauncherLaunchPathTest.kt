package feature.launcher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createApp
import core.data.testing.createPrivateApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
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
    private val mockPopupCard: PopupCardViewModel = mockk(relaxed = true)
    private val mockLaunchable: Launchable = mockk(relaxed = true)
    private val inputFolder: Folder = Folder(folderId = 1L)
    private val inputApp: App = createApp(name = "App", packageName = "com.example.app")

    @Before
    fun setUp() {
        every { mockLaunchable.isDisabled } returns false
        every { mockLaunchable.launch(any()) } returns Unit
        every { mockViewModel.requestLaunch(mockLaunchable) } returns true
    }

    @Test
    fun `a launch reaches onLaunched`() {
        launch(launchable = mockLaunchable)

        verify { mockViewModel.onLaunched(mockLaunchable) }
    }

    @Test
    fun `a disabled Launchable neither launches nor records a Launch count`() {
        every { mockLaunchable.isDisabled } returns true

        launch(launchable = mockLaunchable)

        assertNotLaunched()
    }

    @Test
    fun `a Suspended App launches through the system`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputSuspendedApp: App = createApp(name = "App", packageName = "com.example.app", isSuspended = true)
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        every { mockViewModel.requestLaunch(inputSuspendedApp) } returns true

        launch(launchable = inputSuspendedApp, context = inputContext)

        verify(exactly = 1) {
            mockLauncherApps.startMainActivity(inputSuspendedApp.componentName, inputSuspendedApp.profile, null, null)
        }
        verify { mockViewModel.onLaunched(inputSuspendedApp) }
    }

    @Test
    fun `a declined Paused prompt neither launches nor records a Launch count`() {
        every { mockViewModel.requestLaunch(mockLaunchable) } returns false

        launch(launchable = mockLaunchable)

        assertNotLaunched()
    }

    @Test
    fun `a failed launch does not reach onLaunched`() {
        every { mockLaunchable.launch(any()) } throws ActivityNotFoundException()
        var actualFailed: Launchable? = null

        launch(
            launchable = mockLaunchable,
            effects = effects(onLaunchFailed = { launchable -> actualFailed = launchable })
        )

        assertEquals(mockLaunchable, actualFailed)
        assertNoLaunchRecorded()
    }

    @Test
    fun `a Favorite whose App was uninstalled reports a failed launch instead of crashing`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputGoneApp: App = createApp(name = "Gone", packageName = "com.example.gone")
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every {
            mockLauncherApps.startMainActivity(inputGoneApp.componentName, inputGoneApp.profile, null, null)
        } throws IllegalStateException("The App is uninstalled")
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        every { mockViewModel.requestLaunch(inputGoneApp) } returns true
        var actualFailed: Launchable? = null

        launch(
            launchable = inputGoneApp,
            context = inputContext,
            effects = effects(onLaunchFailed = { launchable -> actualFailed = launchable })
        )

        assertEquals(inputGoneApp, actualFailed)
        assertNoLaunchRecorded()
    }

    @Test
    fun `launching a Folder neither launches nor records a Launch count`() {
        launch(launchable = inputFolder)

        assertNotLaunched()
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

    @Test
    fun `opening a Folder's Pop-up records its Launch count once`() {
        every { mockPopupCard.open(inputFolder, TEST_INPUT_ANCHOR) } returns true

        openPopup(launchable = inputFolder)

        verify(exactly = 1) { mockViewModel.onFolderPopupOpened(inputFolder) }
    }

    @Test
    fun `a Folder Pop-up that does not open records no Launch count`() {
        every { mockPopupCard.open(inputFolder, TEST_INPUT_ANCHOR) } returns false

        openPopup(launchable = inputFolder)

        verify(exactly = 0) { mockViewModel.onFolderPopupOpened(any()) }
    }

    @Test
    fun `opening an App's Pop-up records no Launch count`() {
        every { mockPopupCard.open(inputApp, TEST_INPUT_ANCHOR) } returns true

        openPopup(launchable = inputApp)

        verify(exactly = 0) { mockViewModel.onFolderPopupOpened(any()) }
    }

    private fun launch(launchable: Launchable, context: Context = mockContext, effects: LaunchItemEffects = effects()) {
        launchLauncherItem(launchable = launchable, context = context, viewModel = mockViewModel, effects = effects)
    }

    private fun openPopup(launchable: Launchable) {
        openLauncherItemPopup(
            launchable = launchable,
            anchor = TEST_INPUT_ANCHOR,
            popupCard = mockPopupCard,
            viewModel = mockViewModel
        )
    }

    /** Asserts a launch that did not happen neither opened nor reached onLaunched, so it records no Launch count. */
    private fun assertNotLaunched() {
        verify(exactly = 0) { mockLaunchable.launch(any()) }
        assertNoLaunchRecorded()
    }

    /** Asserts onLaunched was not reached, so no Launch count was recorded. */
    private fun assertNoLaunchRecorded() {
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
