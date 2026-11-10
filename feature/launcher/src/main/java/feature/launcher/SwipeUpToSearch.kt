package feature.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

/**
 * Opens Search on a quick swipe up anywhere on Home. The drag is observed without consuming it,
 * so the Favorites list keeps its scroll: a list that can still scroll forward takes the drag,
 * and only a list that cannot scroll forward opens Search. A hidden Home whose touches are
 * consumed never reacts.
 */
@Composable
internal fun Modifier.swipeUpToSearch(canScrollForward: () -> Boolean, onSwipeUp: () -> Unit): Modifier {
    val density: Float = LocalDensity.current.density
    val currentCanScrollForward: () -> Boolean by rememberUpdatedState(canScrollForward)
    val currentOnSwipeUp: () -> Unit by rememberUpdatedState(onSwipeUp)
    return pointerInput(density) {
        awaitEachGesture {
            val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed) {
                return@awaitEachGesture
            }
            // The list state at the start decides whether the Favorites list owns this drag.
            val canScrollForward: Boolean = currentCanScrollForward()
            trackDrag(
                down = down,
                pass = PointerEventPass.Initial,
                onEnd = { end ->
                    if (end is DragEnd.Released &&
                        SwipeToOpen.onSwipeUpRelease(
                            dx = end.totalDx,
                            dy = end.totalDy,
                            velocityY = end.velocityY,
                            density = density,
                            canScrollForward = canScrollForward
                        )
                    ) {
                        currentOnSwipeUp()
                    }
                }
            )
        }
    }
}
