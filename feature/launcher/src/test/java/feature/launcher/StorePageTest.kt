package feature.launcher

import android.content.Context
import android.content.IntentSender
import android.content.pm.LauncherApps
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createProfileInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PACKAGE_NAME: String = "com.example.app"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class StorePageTest {
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
    private val mockContext: Context = mockk(relaxed = true)
    private val mockLauncherApps: LauncherApps = mockk(relaxed = true)
    private val mockIntentSender: IntentSender = mockk(relaxed = true)

    @Before
    fun serveLauncherApps() {
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(LauncherApps::class.java) } returns mockLauncherApps
    }

    @Test
    fun `opens the Store page the system offers for the Profile`() {
        every { mockLauncherApps.getAppMarketActivityIntent(PACKAGE_NAME, inputPrivateProfile.user) } returns mockIntentSender

        val actualOpened: Boolean = mockContext.openStorePage(packageName = PACKAGE_NAME, profile = inputPrivateProfile.user)

        assertTrue(actualOpened)
        verify(exactly = 1) { mockContext.startIntentSender(mockIntentSender, null, 0, 0, 0, null) }
    }

    @Test
    fun `opens no Store page when the system offers none`() {
        every { mockLauncherApps.getAppMarketActivityIntent(PACKAGE_NAME, inputPrivateProfile.user) } returns null

        val actualOpened: Boolean = mockContext.openStorePage(packageName = PACKAGE_NAME, profile = inputPrivateProfile.user)

        assertFalse(actualOpened)
        verify(exactly = 0) { mockContext.startIntentSender(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `logs a refused market intent without its package`() {
        every {
            mockLauncherApps.getAppMarketActivityIntent(PACKAGE_NAME, inputPrivateProfile.user)
        } throws SecurityException("Could not open $PACKAGE_NAME")
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        var actualOpened: Boolean = true
        logRecorder.record {
            actualOpened = mockContext.openStorePage(packageName = PACKAGE_NAME, profile = inputPrivateProfile.user)
        }

        assertFalse(actualOpened)
        logRecorder.assertLoggedWithout(PACKAGE_NAME)
    }
}
