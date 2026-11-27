package feature.launcher

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import core.data.profiles.IProfileSource
import core.data.testing.TEST_SDK
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.WidgetProvider
import core.ui.widgets.AppWidgetResults
import core.ui.widgets.IAppWidgetViewCache
import core.ui.widgets.WidgetEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WIDGET_ID: Int = 7
private const val WIDGET_PACKAGE: String = "com.example.widget"
private const val WIDGET_PROVIDER_CLASS: String = "com.example.widget.Provider"
private const val PROFILE_SERIAL: Long = 10L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WidgetLaunchTest {
    private val mockActivity: ComponentActivity = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private val mockAppWidgetHostSource: IAppWidgetHostSource = mockk(relaxed = true)
    private val mockAppWidgetViewCache: IAppWidgetViewCache = mockk(relaxed = true)
    private val mockProfileSource: IProfileSource = mockk(relaxed = true)
    private val mockBindLauncher: ManagedActivityResultLauncher<Intent, ActivityResult> = mockk(relaxed = true)
    private val mockPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> = mockk(relaxed = true)
    private val mockNavController: NavController = mockk(relaxed = true)

    @Test
    fun `a failed App Widget bind reports false`() = runTest {
        val inputEvent: WidgetEvent.LaunchBind = WidgetEvent.LaunchBind(
            BindRequest(
                id = AppWidgetId(WIDGET_ID),
                provider = WidgetProvider(packageName = WIDGET_PACKAGE, className = WIDGET_PROVIDER_CLASS),
                profileSerial = PROFILE_SERIAL
            )
        )
        val actualResults: MutableList<Pair<LaunchResult, Boolean>> = mutableListOf()
        every { mockBindLauncher.launch(any()) } throws IllegalStateException("The App Widget bind screen cannot open")
        handleLaunchEvent(inputEvent = inputEvent, callbacks = createCallbacks(actualResults))
        assertEquals(listOf(LaunchResult.Bind to false), actualResults)
        verify(exactly = 1) { mockBindLauncher.launch(any()) }
    }

    @Test
    fun `a failed App Widget configuration reports false to configure`() = runTest {
        val actualResults: MutableList<Pair<LaunchResult, Boolean>> = mutableListOf()
        failConfiguring()
        handleLaunchEvent(inputEvent = WidgetEvent.LaunchConfigure(AppWidgetId(WIDGET_ID)), callbacks = createCallbacks(actualResults))
        assertEquals(listOf(LaunchResult.Configure to false), actualResults)
        verify(exactly = 1) {
            mockAppWidgetHostSource.startConfiguring(mockActivity, AppWidgetId(WIDGET_ID), AppWidgetResults.REQUEST_CODE)
        }
    }

    @Test
    fun `a failed App Widget reconfiguration reports false to reconfigure`() = runTest {
        val actualResults: MutableList<Pair<LaunchResult, Boolean>> = mutableListOf()
        failConfiguring()
        handleLaunchEvent(inputEvent = WidgetEvent.LaunchReconfigure(AppWidgetId(WIDGET_ID)), callbacks = createCallbacks(actualResults))
        assertEquals(listOf(LaunchResult.Reconfigure to false), actualResults)
        verify(exactly = 1) {
            mockAppWidgetHostSource.startConfiguring(mockActivity, AppWidgetId(WIDGET_ID), AppWidgetResults.RECONFIGURE_REQUEST_CODE)
        }
    }

    @Test
    fun `App Widget configuration without an Activity reports false without launching`() = runTest {
        val actualResults: MutableList<Pair<LaunchResult, Boolean>> = mutableListOf()
        handleLaunchEvent(
            inputEvent = WidgetEvent.LaunchConfigure(AppWidgetId(WIDGET_ID)),
            callbacks = createCallbacks(actualResults),
            activity = null
        )
        assertEquals(listOf(LaunchResult.Configure to false), actualResults)
        verify(exactly = 0) { mockAppWidgetHostSource.startConfiguring(any(), any(), any()) }
    }

    private fun failConfiguring() {
        every {
            mockAppWidgetHostSource.startConfiguring(any(), any(), any())
        } throws IllegalStateException("The App Widget configuration screen cannot open")
    }

    private suspend fun TestScope.handleLaunchEvent(
        inputEvent: WidgetEvent,
        callbacks: WidgetEventCallbacks,
        activity: ComponentActivity? = mockActivity
    ) {
        handleWidgetEvent(
            event = inputEvent,
            activity = activity,
            context = mockContext,
            coroutineScope = backgroundScope,
            snackbarHostState = SnackbarHostState(),
            appWidgetHostSource = mockAppWidgetHostSource,
            appWidgetViewCache = mockAppWidgetViewCache,
            profileSource = mockProfileSource,
            onRequestQuietModeOff = {},
            bindLauncher = mockBindLauncher,
            permissionLauncher = mockPermissionLauncher,
            navController = mockNavController,
            callbacks = callbacks,
            showCalendarPermissionDialog = {},
            showNotificationAccessDialog = {}
        )
    }

    private fun createCallbacks(actualResults: MutableList<Pair<LaunchResult, Boolean>>): WidgetEventCallbacks = WidgetEventCallbacks(
        onBindResult = { result: Boolean -> actualResults += LaunchResult.Bind to result },
        onConfigureResult = { result: Boolean -> actualResults += LaunchResult.Configure to result },
        onReconfigureResult = { result: Boolean -> actualResults += LaunchResult.Reconfigure to result },
        onScrollToPlace = {},
        onUndo = {},
        onEditFavorites = {}
    )

    /** The launch callback a Widget event reported its result through. */
    private enum class LaunchResult {
        Bind,
        Configure,
        Reconfigure
    }
}
