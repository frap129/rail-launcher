package core.ui.composables

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

private const val STYLUS_BUTTON_STATE: Int = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY

/**
 * Opens the row's long-press menu on a secondary click: the mouse's secondary button or a stylus
 * button. The press is consumed on the Initial pass so the row's tap gesture never turns it into a
 * tap, and a press an ancestor already consumed, such as a widget-edit or Pop-up scrim, is ignored.
 */
fun Modifier.longPressMenuOnSecondaryClick(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    var isMenuButtonHeld: Boolean = false
    var wasPointerOverRow: Boolean = false
    awaitPointerEventScope {
        while (true) {
            val event: PointerEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            if (event.changes.any { change -> change.isConsumed }) {
                isMenuButtonHeld = false
                continue
            }
            val isMenuButtonPressed: Boolean = event.opensLongPressMenu()
            if (isMenuButtonPressed && !isMenuButtonHeld && event.startsOnThisRow(wasPointerOverRow = wasPointerOverRow)) {
                onLongPress()
            }
            if (isMenuButtonPressed) {
                event.changes.forEach { change -> change.consume() }
            }
            isMenuButtonHeld = isMenuButtonPressed
            wasPointerOverRow = event.type != PointerEventType.Exit
        }
    }
}

/** True when a secondary click carries the menu button: the secondary mouse button or a stylus button. */
private fun PointerEvent.opensLongPressMenu(): Boolean = buttons.isSecondaryPressed || isStylusButtonPressed()

/**
 * True when this event starts the press on this row, so a secondary-click drag across rows opens
 * only the menu of the row where the press began. A stylus button pressed while the pointer already
 * hovers the row has no pointer down, yet still starts a press.
 */
private fun PointerEvent.startsOnThisRow(wasPointerOverRow: Boolean): Boolean {
    val isPointerPressed: Boolean = changes.any { change: PointerInputChange -> change.pressed }
    if (isPointerPressed) {
        return changes.any { change: PointerInputChange -> change.pressed && !change.previousPressed }
    }
    return wasPointerOverRow && isStylusButtonPressed()
}

/** True when the stylus reports its button pressed, which Compose may classify as either button. */
private fun PointerEvent.isStylusButtonPressed(): Boolean = ((motionEvent?.buttonState ?: 0) and STYLUS_BUTTON_STATE) != 0
