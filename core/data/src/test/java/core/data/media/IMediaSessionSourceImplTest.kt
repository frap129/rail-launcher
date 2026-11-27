package core.data.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import core.data.notifications.isNotificationAccessGranted
import core.data.testing.RAIL_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.failActivityStarts
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val NOTIFICATION_ACCESS_CLASS: String = "core.data.notifications.NotificationAccessKt"
private const val SESSION_TAG: String = "Media Player launch test"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IMediaSessionSourceImplTest {
    @Before
    fun setUp() {
        mockkStatic(NOTIFICATION_ACCESS_CLASS)
        every { isNotificationAccessGranted(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(NOTIFICATION_ACCESS_CLASS)
    }

    @Test
    fun `a failed Media Player launch reports false`() = runTest {
        val inputSession: MediaSession = MediaSession(RuntimeEnvironment.getApplication(), SESSION_TAG)
        try {
            val inputController: MediaController = inputSession.controller
            val inputPackageName: String = inputController.packageName
            val inputKey: MediaSessionKey = MediaSessionKey(inputController.sessionToken.toString())
            val inputLaunchIntent: Intent = Intent(Intent.ACTION_MAIN).setPackage(inputPackageName)
            val mockContext: Context = mockk(relaxed = true)
            val mockPackageManager: PackageManager = mockk(relaxed = true)
            val mockSessionManager: MediaSessionManager = mockk(relaxed = true)
            val mockArtworkStore: MediaArtworkStore = mockk(relaxed = true)
            every { mockContext.applicationContext } returns mockContext
            every { mockContext.packageName } returns RAIL_PACKAGE_NAME
            every { mockContext.packageManager } returns mockPackageManager
            every { mockContext.getSystemService(MediaSessionManager::class.java) } returns mockSessionManager
            mockContext.failActivityStarts()
            every { mockPackageManager.getLaunchIntentForPackage(inputPackageName) } returns inputLaunchIntent
            every { mockSessionManager.getActiveSessions(any()) } returns listOf(inputController)
            val source: IMediaSessionSourceImpl = IMediaSessionSourceImpl(
                context = mockContext,
                scope = backgroundScope,
                artworkStore = mockArtworkStore
            )
            backgroundScope.launch { source.sessions.collect() }
            runCurrent()
            val actualResult: Boolean = source.open(inputKey)
            assertFalse(actualResult)
            verify(exactly = 1) { mockContext.startActivity(any()) }
        } finally {
            backgroundScope.cancel()
            runCurrent()
            inputSession.release()
        }
    }
}
