package core.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Consumes every touch in this area, optionally reporting the end of the gesture. */
fun Modifier.consumeTouches(onGestureEnd: (() -> Unit)? = null): Modifier = pointerInput(onGestureEnd) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
        var pressed: Boolean = true
        while (pressed) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            event.changes.forEach { change -> change.consume() }
            pressed = event.changes.any { change -> change.pressed }
        }
        onGestureEnd?.invoke()
    }
}
