package feature.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import core.data.testing.TEST_SDK
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PinShortcutActivityTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ShadowToast.reset()
    }

    @Test
    fun `an invalid Shortcut request shows the add error and finishes`() {
        val inputIntent: Intent = createIntent(
            request = createPinRequest(requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT, isValid = false)
        )

        val actualActivity: PinShortcutActivity = Robolectric.buildActivity(PinShortcutActivity::class.java, inputIntent).create().get()

        assertTrue(actualActivity.isFinishing)
        assertEquals(context.getString(R.string.couldnt_add_shortcut), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a non-Shortcut request finishes without a message`() {
        val inputIntent: Intent = createIntent(
            request = createPinRequest(requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET, isValid = true)
        )

        val actualActivity: PinShortcutActivity = Robolectric.buildActivity(PinShortcutActivity::class.java, inputIntent).create().get()

        assertTrue(actualActivity.isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a recreated add finishes without showing the add error`() {
        val inputIntent: Intent = createIntent(
            request = createPinRequest(requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT, isValid = false)
        )
        val inputState: Bundle = Bundle().apply { putBoolean("add_in_progress", true) }

        val actualActivity: PinShortcutActivity =
            Robolectric.buildActivity(PinShortcutActivity::class.java, inputIntent).create(inputState).get()

        assertTrue(actualActivity.isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    private fun createIntent(request: LauncherApps.PinItemRequest): Intent =
        Intent(context, PinShortcutActivity::class.java).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, request)

    private fun createPinRequest(requestType: Int, isValid: Boolean): LauncherApps.PinItemRequest {
        val innerType: Class<*> = Class.forName("android.content.pm.IPinItemRequest")
        val mockInner: Any = Proxy.newProxyInstance(innerType.classLoader, arrayOf(innerType)) { _, method, _ ->
            when (method.name) {
                "isValid" -> isValid
                "getExtras" -> Bundle()
                else -> null
            }
        }
        val constructor = LauncherApps.PinItemRequest::class.java.getConstructor(innerType, Int::class.javaPrimitiveType)
        return constructor.newInstance(mockInner, requestType)
    }
}
