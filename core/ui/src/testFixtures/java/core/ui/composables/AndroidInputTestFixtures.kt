package core.ui.composables

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot

private const val EVENT_TIME_MILLIS: Long = 0L
private const val POINTER_ID: Int = 0
private const val POINTER_COUNT: Int = 1
private const val POINTER_PRESSURE: Float = 1f
private const val POINTER_SIZE: Float = 1f
private const val UNIT_PRECISION: Float = 1f
private const val NO_BUTTON_STATE: Int = 0
private const val NO_DEVICE_ID: Int = 0
private const val NO_FLAGS: Int = 0
private const val NO_META_STATE: Int = 0
private const val NO_REPEAT_COUNT: Int = 0
private const val NO_SCAN_CODE: Int = 0

/** Returns the Android root View backing this Compose test. */
fun ComposeContentTestRule.rootView(): View = (requireNotNull(onRoot().fetchSemanticsNode().root) as ViewRootForTest).view

/** Dispatches an Android key press and release to the Compose root View. */
fun ComposeContentTestRule.pressKey(keyCode: Int, metaState: Int) {
    val actualView: View = rootView()
    runOnIdle {
        actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_DOWN, keyCode = keyCode, metaState = metaState))
        actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_UP, keyCode = keyCode, metaState = metaState))
    }
}

/** Dispatches a stylus down and up pair to [node]. */
fun ComposeContentTestRule.pressStylus(node: SemanticsNodeInteraction, buttonState: Int) {
    val actualNode: SemanticsNode = node.fetchSemanticsNode()
    val actualView: View = rootView()
    val inputCenter: Offset = actualNode.boundsInRoot.center
    runOnIdle {
        actualView.dispatchTouchEvent(
            inputCenter.toMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                buttonState = buttonState,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS
            )
        )
        actualView.dispatchTouchEvent(
            inputCenter.toMotionEvent(
                action = MotionEvent.ACTION_UP,
                buttonState = NO_BUTTON_STATE,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS
            )
        )
    }
    waitForIdle()
}

/** Dispatches an Android hover event to [node]. */
fun ComposeContentTestRule.dispatchMouseHover(node: SemanticsNodeInteraction, action: Int, buttonState: Int) {
    val actualNode: SemanticsNode = node.fetchSemanticsNode()
    val actualView: View = rootView()
    val inputCenter: Offset = actualNode.boundsInRoot.center
    runOnIdle {
        actualView.dispatchHoverEventForTest(
            inputCenter.toMotionEvent(
                action = action,
                buttonState = buttonState,
                toolType = MotionEvent.TOOL_TYPE_MOUSE,
                source = InputDevice.SOURCE_MOUSE
            )
        )
    }
    waitForIdle()
}

private fun View.dispatchHoverEventForTest(motionEvent: MotionEvent) {
    View::class.java.getDeclaredMethod("dispatchHoverEvent", MotionEvent::class.java).apply {
        isAccessible = true
    }.invoke(this, motionEvent)
}

private fun Offset.toMotionEvent(action: Int, buttonState: Int, toolType: Int, source: Int): MotionEvent {
    val inputProperties: MotionEvent.PointerProperties = MotionEvent.PointerProperties().apply {
        id = POINTER_ID
        this.toolType = toolType
    }
    val inputCoordinates: MotionEvent.PointerCoords = MotionEvent.PointerCoords().apply {
        x = this@toMotionEvent.x
        y = this@toMotionEvent.y
        pressure = POINTER_PRESSURE
        size = POINTER_SIZE
    }
    return MotionEvent.obtain(
        EVENT_TIME_MILLIS,
        EVENT_TIME_MILLIS,
        action,
        POINTER_COUNT,
        arrayOf(inputProperties),
        arrayOf(inputCoordinates),
        NO_META_STATE,
        buttonState,
        UNIT_PRECISION,
        UNIT_PRECISION,
        NO_DEVICE_ID,
        NO_FLAGS,
        source,
        NO_FLAGS
    )
}

private fun createKeyEvent(action: Int, keyCode: Int, metaState: Int): KeyEvent = KeyEvent(
    EVENT_TIME_MILLIS,
    EVENT_TIME_MILLIS,
    action,
    keyCode,
    NO_REPEAT_COUNT,
    metaState,
    NO_DEVICE_ID,
    NO_SCAN_CODE,
    NO_FLAGS
)
