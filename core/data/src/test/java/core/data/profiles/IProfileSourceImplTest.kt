package core.data.profiles

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.TEST_SDK_36
import core.data.testing.WORK_USER_ID
import core.data.testing.createIntentSender
import core.data.testing.createUserHandle
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val OTHER_USER_ID: Int = 12

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IProfileSourceImplTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val personalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val workUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val privateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val otherUser: UserHandle = createUserHandle(userId = OTHER_USER_ID)

    @Suppress("DEPRECATION")
    @Test
    fun `maps LauncherUserInfo types to ProfileKind`() {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.profiles } returns listOf(personalUser, workUser, privateUser, otherUser)
        every { mockLauncherApps.getLauncherUserInfo(any()) } answers {
            launcherUserInfo(user = firstArg(), userType = userTypeFor(user = firstArg()))
        }
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)

        val source: IProfileSourceImpl = IProfileSourceImpl(application)

        assertEquals(
            mapOf(
                personalUser to ProfileKind.Personal,
                workUser to ProfileKind.Work,
                privateUser to ProfileKind.Private,
                otherUser to ProfileKind.Other
            ),
            source.profiles.value.associate { profile -> profile.user to profile.kind }
        )
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `reads the Private Space entrypoint flag again when the user config changes`() {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.profiles } returns listOf(privateUser)
        every { mockLauncherApps.privateSpaceSettingsIntent } returns createIntentSender(context = application)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        every { mockLauncherApps.registerCallback(capture(callbackSlot), any()) } just Runs
        val mockUserInfo: LauncherUserInfo = launcherUserInfo(user = privateUser, userType = UserManager.USER_TYPE_PROFILE_PRIVATE)
        every { mockUserInfo.userConfig } returns entrypointConfig(hidden = false)
        every { mockLauncherApps.getLauncherUserInfo(any()) } returns mockUserInfo
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val source: IProfileSourceImpl = IProfileSourceImpl(application)

        assertFalse(source.profiles.value.single().isEntrypointHidden)

        every { mockUserInfo.userConfig } returns entrypointConfig(hidden = true)
        callbackSlot.captured.onUserConfigChanged(mockUserInfo)

        assertTrue(source.profiles.value.single().isEntrypointHidden)
        assertNotNull(source.privateSpaceSettingsIntent())
    }

    @Test
    fun `offers no Private Space settings below Android 16`() {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.profiles } returns emptyList<UserHandle>()
        every { mockLauncherApps.privateSpaceSettingsIntent } returns createIntentSender(context = application)
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val source: IProfileSourceImpl = IProfileSourceImpl(application)

        assertNull(source.privateSpaceSettingsIntent())
    }

    @Test
    fun `maps the quiet request outcomes`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.profiles } returns listOf(workUser)
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val mockUserManager: UserManager = mockk(relaxed = true)
        shadowOf(application).setSystemService(Context.USER_SERVICE, mockUserManager)
        val source: IProfileSourceImpl = IProfileSourceImpl(application)

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } returns true
        assertEquals(QuietRequestResult.Changed, source.requestQuiet(user = workUser, quiet = true))

        every { mockUserManager.requestQuietModeEnabled(false, workUser) } returns false
        assertEquals(QuietRequestResult.AwaitingCredential, source.requestQuiet(user = workUser, quiet = false))

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } throws SecurityException()
        assertEquals(QuietRequestResult.Failed, source.requestQuiet(user = workUser, quiet = true))

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } throws IllegalArgumentException()
        assertEquals(QuietRequestResult.Failed, source.requestQuiet(user = workUser, quiet = true))
    }

    private fun launcherUserInfo(user: UserHandle, userType: String): LauncherUserInfo {
        val mockUserInfo: LauncherUserInfo = mockk(relaxed = true)
        every { mockUserInfo.userType } returns userType
        return mockUserInfo
    }

    private fun entrypointConfig(hidden: Boolean): Bundle = Bundle().apply {
        putBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, hidden)
    }

    private fun userTypeFor(user: UserHandle): String = when (user) {
        workUser -> UserManager.USER_TYPE_PROFILE_MANAGED
        privateUser -> UserManager.USER_TYPE_PROFILE_PRIVATE
        else -> UserManager.USER_TYPE_PROFILE_CLONE
    }
}
