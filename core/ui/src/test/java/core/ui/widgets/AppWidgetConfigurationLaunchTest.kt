package core.ui.widgets

import androidx.activity.ComponentActivity
import core.data.testing.TEST_SDK
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WIDGET_ID: Int = 7

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class AppWidgetConfigurationLaunchTest {
    private val mockActivity: ComponentActivity = mockk(relaxed = true)
    private val mockAppWidgetHostSource: IAppWidgetHostSource = mockk(relaxed = true)

    @Test
    fun `failed App Widget configuration launches report false`() {
        val inputId: AppWidgetId = AppWidgetId(WIDGET_ID)
        val actualResults: MutableList<Boolean> = mutableListOf()
        val onLaunchFailed: () -> Unit = { actualResults += false }
        every { mockAppWidgetHostSource.startConfiguring(any(), any(), any()) } throws IllegalStateException("Cannot configure")
        launchAppWidgetConfiguration(
            activity = mockActivity,
            appWidgetHostSource = mockAppWidgetHostSource,
            id = inputId,
            requestCode = AppWidgetResults.REQUEST_CODE,
            onLaunchFailed = onLaunchFailed
        )
        launchAppWidgetConfiguration(
            activity = mockActivity,
            appWidgetHostSource = mockAppWidgetHostSource,
            id = inputId,
            requestCode = AppWidgetResults.RECONFIGURE_REQUEST_CODE,
            onLaunchFailed = onLaunchFailed
        )
        assertEquals(listOf(false, false), actualResults)
        verify(exactly = 2) { mockAppWidgetHostSource.startConfiguring(any(), any(), any()) }
    }
}
