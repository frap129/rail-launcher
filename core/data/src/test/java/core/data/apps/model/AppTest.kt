package core.data.apps.model

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `reports uninstallable only for a removable App whose Profile allows it`() {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputApp: App = appInProfile(profile = inputProfile)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            uninstallRestrictedUsers += inputProfile.user
        }
        val mockAllowedProfileSource = createProfileSourceMock(profile = inputProfile)
        val mockRestrictedProfileSource = createProfileSourceMock(state = inputProfileState)

        assertTrue(inputApp.canUninstall(mockAllowedProfileSource))
        assertFalse(inputApp.canUninstall(mockRestrictedProfileSource))
        assertFalse(inputApp.copy(isRemovable = false).canUninstall(mockAllowedProfileSource))
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
        val inputPersonalApp: App = appInProfile(profile = inputPersonalProfile)
        val inputWorkApp: App = appInProfile(profile = inputWorkProfile)
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

    @Test
    fun `requests uninstall in each App's own Profile`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputWorkProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputPersonalApp: App = appInProfile(profile = inputPersonalProfile)
        val inputWorkApp: App = appInProfile(profile = inputWorkProfile)

        inputPersonalApp.requestUninstall(inputContext)
        inputWorkApp.requestUninstall(inputContext)

        val actualIntents: List<Intent> = List(size = 2) { shadowOf(inputContext).nextStartedActivity }
        assertTrue(
            actualIntents.all { intent ->
                intent.action == Intent.ACTION_DELETE && intent.data == Uri.fromParts("package", PACKAGE_NAME, null)
            }
        )
        assertEquals(
            setOf(inputPersonalProfile.user, inputWorkProfile.user),
            actualIntents.map { intent -> intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java) }.toSet()
        )
    }

    @Test
    fun `logs a private App uninstall failure without its package or key`() {
        val inputPrivateApp: App = createPrivateApp()
        val mockContext: Context = mockk()
        every { mockContext.startActivity(any()) } throws SecurityException("Could not uninstall ${inputPrivateApp.key}")
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record { inputPrivateApp.requestUninstall(mockContext) }

        logRecorder.assertLoggedWithout(inputPrivateApp.packageName, inputPrivateApp.key)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `logs a private App info failure without its package or key`() {
        val inputContext: Application = RuntimeEnvironment.getApplication()
        val inputPrivateApp: App = createPrivateApp()
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every {
            mockLauncherApps.startAppDetailsActivity(inputPrivateApp.componentName, inputPrivateApp.profile, null, null)
        } throws SecurityException("Could not open ${inputPrivateApp.key}")
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record { inputPrivateApp.openAppInfo(inputContext) }

        logRecorder.assertLoggedWithout(inputPrivateApp.packageName, inputPrivateApp.key)
    }

    private fun appInProfile(profile: ProfileInfo): App = createApp(name = "Example", packageName = PACKAGE_NAME, profileInfo = profile)
}
