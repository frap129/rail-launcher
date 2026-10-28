package core.data.apps.model

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createUserHandle
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PACKAGE_NAME: String = "com.example.app"
private const val ACTIVITY_NAME: String = "com.example.app.MainActivity"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppTest {
    @Test
    fun `keys an App by its package and Profile`() {
        val inputWorkUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
        val inputApp: App = createApp(profile = inputWorkUser)

        assertEquals("com.example.app_UserHandle{10}", inputApp.key)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `opens App info in each App's own Profile`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPersonalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
        val inputWorkUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
        val inputPersonalApp: App = createApp(profile = inputPersonalUser)
        val inputWorkApp: App = createApp(profile = inputWorkUser)
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)

        inputPersonalApp.openAppInfo(inputContext)
        inputWorkApp.openAppInfo(inputContext)

        verify(exactly = 1) {
            mockLauncherApps.startAppDetailsActivity(inputPersonalApp.componentName, inputPersonalUser, null, null)
        }
        verify(exactly = 1) {
            mockLauncherApps.startAppDetailsActivity(inputWorkApp.componentName, inputWorkUser, null, null)
        }
    }

    private fun createApp(profile: UserHandle): App = App(
        defaultName = "Example",
        profile = profile,
        componentName = ComponentName(PACKAGE_NAME, ACTIVITY_NAME)
    )
}
