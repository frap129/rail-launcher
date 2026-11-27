package core.ui.widgets

import android.content.Context
import android.content.Intent
import android.provider.Settings
import core.data.testing.RAIL_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.failActivityStarts
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class NotificationAccessTest {
    @Test
    fun `a failed listener detail launch falls back to notification access settings`() {
        val mockContext: Context = mockk(relaxed = true)
        val actualStartedActions: MutableList<String> = mutableListOf()
        every { mockContext.packageName } returns RAIL_PACKAGE_NAME
        mockContext.failActivityStarts(
            shouldFail = { inputIntent: Intent ->
                inputIntent.action == Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
            },
            onStart = { inputIntent: Intent -> actualStartedActions.add(inputIntent.action.orEmpty()) }
        )
        mockContext.openRailNotificationListenerSettings()
        assertEquals(
            listOf(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            actualStartedActions
        )
    }
}
