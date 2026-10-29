package core.data.apps.model

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import core.data.icons.model.Icon
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createProfileInfo
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppTest {
    @Test
    fun `keys an App by its activity component and Profile`() {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputApp: App = createApp(name = "Example", packageName = PACKAGE_NAME, profileInfo = inputProfile)

        assertEquals("com.example.app/com.example.app.MainActivity_UserHandle{10}", inputApp.key)
    }

    @Test
    fun `launches its own launcher activity`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputApp: App = createApp(name = "Example", packageName = PACKAGE_NAME).copy(
            componentName = ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.OtherActivity")
        )
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)

        inputApp.launch(inputContext)

        verify(exactly = 1) {
            mockLauncherApps.startMainActivity(inputApp.componentName, inputApp.profile, null, null)
        }
    }

    @Test
    fun `reads the badged label for a work App`() {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputApp: App = createApp(
            name = "Mail",
            packageName = PACKAGE_NAME,
            profileInfo = inputProfile,
            badgedLabel = "Work Gmail"
        )

        assertEquals("Work Gmail", inputApp.accessibilityName)
    }

    @Test
    fun `reads its name for a personal App`() {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputApp: App = createApp(name = "Mail", packageName = PACKAGE_NAME, profileInfo = inputProfile)

        assertEquals("Mail", inputApp.accessibilityName)
    }

    @Test
    fun `badges the default icon of a non-personal App`() {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputApp: App = createApp(name = "Example", packageName = PACKAGE_NAME, profileInfo = inputProfile)

        assertEquals(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(inputApp.componentName), profile = inputProfile.user),
            inputApp.defaultIcon
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `opens App info in each App's own Profile`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputWorkProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputPersonalApp: App = createApp(name = "Example", packageName = PACKAGE_NAME, profileInfo = inputPersonalProfile)
        val inputWorkApp: App = createApp(name = "Example", packageName = PACKAGE_NAME, profileInfo = inputWorkProfile)
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)

        inputPersonalApp.openAppInfo(inputContext)
        inputWorkApp.openAppInfo(inputContext)

        verify(exactly = 1) {
            mockLauncherApps.startAppDetailsActivity(
                inputPersonalApp.componentName,
                inputPersonalProfile.user,
                null,
                null
            )
        }
        verify(exactly = 1) {
            mockLauncherApps.startAppDetailsActivity(inputWorkApp.componentName, inputWorkProfile.user, null, null)
        }
    }
}
