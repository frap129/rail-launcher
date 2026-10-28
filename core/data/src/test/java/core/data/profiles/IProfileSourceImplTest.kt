package core.data.profiles

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.os.UserHandle
import android.os.UserManager
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createUserHandle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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

    private fun launcherUserInfo(user: UserHandle, userType: String): LauncherUserInfo {
        val mockUserInfo: LauncherUserInfo = mockk(relaxed = true)
        every { mockUserInfo.userType } returns userType
        return mockUserInfo
    }

    private fun userTypeFor(user: UserHandle): String = when (user) {
        workUser -> UserManager.USER_TYPE_PROFILE_MANAGED
        privateUser -> UserManager.USER_TYPE_PROFILE_PRIVATE
        else -> UserManager.USER_TYPE_PROFILE_CLONE
    }
}
