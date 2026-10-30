package feature.launcher.widgets

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import core.data.apps.model.App
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createPrivateApp
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class WidgetAreaLaunchTest {
    @Test
    fun `logs a private Launchable launch failure without its name, package or key`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPrivateApp: App = createPrivateApp()
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every {
            mockLauncherApps.startMainActivity(inputPrivateApp.componentName, inputPrivateApp.profile, null, null)
        } throws SecurityException("Could not open ${inputPrivateApp.key}")
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record { inputContext.openLaunchable(inputPrivateApp) }

        logRecorder.assertLoggedWithout(inputPrivateApp.name, inputPrivateApp.packageName, inputPrivateApp.key)
    }
}
