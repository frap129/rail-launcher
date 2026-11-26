package core.ui.widgets

import android.app.Activity
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class RailAppWidgetHostViewTest {
    @Test
    fun `long press invokes its callback without performing native haptic feedback`() {
        val inputActivity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val inputHostView: RailAppWidgetHostView = RailAppWidgetHostView(inputActivity)
        inputActivity.setContentView(inputHostView)
        var actualLongPressCount: Int = 0
        inputHostView.onLongPress = { actualLongPressCount++ }
        // Seed a non-zero value because ShadowView and LONG_PRESS both default to zero.
        inputHostView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val expectedHapticFeedbackType: Int = shadowOf(inputHostView).lastHapticFeedbackPerformed()
        val inputDownTime: Long = SystemClock.uptimeMillis()
        val inputDownEvent: MotionEvent = MotionEvent.obtain(
            inputDownTime,
            inputDownTime,
            MotionEvent.ACTION_DOWN,
            0f,
            0f,
            0
        )
        inputHostView.onInterceptTouchEvent(inputDownEvent)
        inputDownEvent.recycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, actualLongPressCount)
        assertEquals(expectedHapticFeedbackType, shadowOf(inputHostView).lastHapticFeedbackPerformed())
    }
}
