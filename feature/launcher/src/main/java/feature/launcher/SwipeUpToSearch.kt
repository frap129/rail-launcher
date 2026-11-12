package feature.launcher

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import core.ui.util.performSearchSwipeHaptic
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The rubber band Home pulls up on once its lists reach their end. It collects the upward drag
 * the lists leave unconsumed, plays a firm haptic when the pull arms Search, opens Search on a
 * release while armed, and springs back otherwise.
 */
@Stable
private class SwipeUpRubberBand(
    private val coroutineScope: CoroutineScope,
    private val density: () -> Float,
    private val isEnabled: () -> Boolean,
    private val isWidgetTakenSwipe: () -> Boolean,
    private val onArmed: () -> Unit,
    private val onSwipeUp: () -> Unit
) : NestedScrollConnection {
    var pullPx: Float by mutableFloatStateOf(0f)
        private set

    private var settleJob: Job? = null

    private var hasPulledThisGesture: Boolean = false

    private var isGestureCancelled: Boolean = false

    private val isArmed: Boolean get() = SwipeToOpen.isSwipeUpArmed(pullPx = pullPx, density = density())

    val offsetPx: Float get() = SwipeToOpen.rubberBandOffset(pullPx = pullPx, density = density())

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || pullPx <= 0f || available.y <= 0f) {
            return Offset.Zero
        }
        val releasedPx: Float = min(available.y, pullPx)
        pull(toPx = pullPx - releasedPx)
        return Offset(x = 0f, y = releasedPx)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || !isEnabled() || isWidgetTakenSwipe()) {
            return Offset.Zero
        }
        // The band stops at its end; extra upward drag is held, so dragging back responds at once.
        pull(toPx = min(pullPx - available.y, SwipeToOpen.SWIPE_UP_ARM_PULL_DP * density()))
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pullPx <= 0f) {
            return Velocity.Zero
        }
        if (isArmed && isEnabled() && !isWidgetTakenSwipe() && !isGestureCancelled) {
            reset()
            onSwipeUp()
        } else {
            settle()
        }
        return available
    }

    /** Cancels any settle so a new gesture catches Home where it is. */
    fun catch() {
        settleJob?.cancel()
        hasPulledThisGesture = false
        isGestureCancelled = false
    }

    /**
     * Ends the gesture [catch] started. A cancelled gesture never opens Search, and a gesture
     * that never pulled Home, such as a tap during a settle, springs Home back itself because no
     * fling follows it.
     */
    fun release(isCancelled: Boolean) {
        isGestureCancelled = isCancelled
        if (pullPx != 0f && (isCancelled || !hasPulledThisGesture)) {
            settle()
        }
    }

    fun reset() {
        settleJob?.cancel()
        pullPx = 0f
    }

    private fun pull(toPx: Float) {
        settleJob?.cancel()
        hasPulledThisGesture = true
        val wasArmed: Boolean = isArmed
        pullPx = toPx
        if (isArmed && !wasArmed) {
            onArmed()
        }
    }

    /** Springs Home back to rest. */
    fun settle() {
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            animate(initialValue = pullPx, targetValue = 0f, animationSpec = spring()) { value, _ ->
                pullPx = value
            }
        }
    }
}

/**
 * Opens Search on a swipe up past the end of Home. Home's lists keep their normal scroll; the
 * upward drag they leave once they reach their end pulls Home up on a rubber band, a firm haptic
 * confirms the pull has armed Search, and releasing while armed opens it. An App Widget that
 * takes the drag never pulls Home. A hidden Home whose touches are consumed never reacts.
 */
@Composable
internal fun Modifier.swipeUpToSearch(enabled: Boolean, widgetSwipeState: WidgetSwipeState, onSwipeUp: () -> Unit): Modifier {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val density: Float = LocalDensity.current.density
    val currentDensity: Float by rememberUpdatedState(density)
    val currentEnabled: Boolean by rememberUpdatedState(enabled)
    val currentOnSwipeUp: () -> Unit by rememberUpdatedState(onSwipeUp)
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val rubberBand: SwipeUpRubberBand = remember(widgetSwipeState) {
        SwipeUpRubberBand(
            coroutineScope = coroutineScope,
            density = { currentDensity },
            isEnabled = { currentEnabled },
            isWidgetTakenSwipe = { widgetSwipeState.isWidgetTakenSwipe },
            onArmed = { hapticFeedback.performSearchSwipeHaptic() },
            onSwipeUp = { currentOnSwipeUp() }
        )
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            rubberBand.reset()
        }
    }
    LaunchedEffect(rubberBand) {
        snapshotFlow { widgetSwipeState.isWidgetTakenSwipe }
            .filter { isTaken -> isTaken }
            .collect { rubberBand.settle() }
    }
    return this
        .offset { IntOffset(x = 0, y = -rubberBand.offsetPx.roundToInt()) }
        .pointerInput(rubberBand) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                widgetSwipeState.reset()
                rubberBand.catch()
                val release: PointerEvent = awaitRelease()
                // A cancelled gesture arrives as an already consumed release.
                rubberBand.release(isCancelled = release.changes.any { change -> change.isConsumed })
            }
        }
        .nestedScroll(rubberBand)
}

/** Waits, ahead of Home's lists, for the event that lifts the last pointer. */
private suspend fun AwaitPointerEventScope.awaitRelease(): PointerEvent {
    var event: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)
    while (event.changes.any { change -> change.pressed }) {
        event = awaitPointerEvent(PointerEventPass.Initial)
    }
    return event
}
