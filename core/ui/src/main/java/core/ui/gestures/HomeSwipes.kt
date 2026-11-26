package core.ui.gestures

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
import core.ui.util.performHomeSwipeHaptic
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The rubber band Home pulls on once its lists reach an edge. It collects the vertical drag the
 * lists leave unconsumed as a signed pull (negative is up), plays a firm haptic when the pull arms
 * its swipe, runs the swipe on a release while armed, and springs back otherwise.
 */
@Stable
private class HomeRubberBand(
    private val coroutineScope: CoroutineScope,
    private val density: () -> Float,
    private val isSwipeUpEnabled: () -> Boolean,
    private val isSwipeDownEnabled: () -> Boolean,
    private val isWidgetTakenSwipe: () -> Boolean,
    private val onArmed: () -> Unit,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit
) : NestedScrollConnection {
    var pullPx: Float by mutableFloatStateOf(0f)
        private set

    private var settleJob: Job? = null

    private var hasPulledThisGesture: Boolean = false

    private var isGestureCancelled: Boolean = false

    private val isArmed: Boolean get() = SwipeToOpen.isHomePullArmed(pullPx = abs(pullPx), density = density())

    val offsetPx: Float get() = sign(pullPx) * SwipeToOpen.rubberBandOffset(pullPx = abs(pullPx), density = density())

    /** Whether the swipe toward [towardPx]'s direction is on. */
    fun isEnabled(towardPx: Float): Boolean = if (towardPx < 0f) isSwipeUpEnabled() else isSwipeDownEnabled()

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || pullPx == 0f || sign(available.y) != -sign(pullPx)) {
            return Offset.Zero
        }
        val releasedPx: Float = sign(available.y) * min(abs(available.y), abs(pullPx))
        pull(toPx = pullPx + releasedPx)
        return Offset(x = 0f, y = releasedPx)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f || !isEnabled(available.y) || isWidgetTakenSwipe()) {
            return Offset.Zero
        }
        // The band stops at its end; extra drag is held, so dragging back responds at once.
        val maxPullPx: Float = SwipeToOpen.HOME_PULL_ARM_DP * density()
        pull(toPx = (pullPx + available.y).coerceIn(-maxPullPx, maxPullPx))
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pullPx == 0f) {
            return Velocity.Zero
        }
        if (isArmed && isEnabled(pullPx) && !isWidgetTakenSwipe() && !isGestureCancelled) {
            val wasPulledUp: Boolean = pullPx < 0f
            reset()
            if (wasPulledUp) onSwipeUp() else onSwipeDown()
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
     * Ends the gesture [catch] started. A cancelled gesture never runs the swipe, and a gesture
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
 * Runs a swipe past the edges of Home: up past its end opens Search, down past its start expands
 * the notification shade. Home's lists keep their normal scroll; the drag they leave once they
 * reach an edge pulls Home on a rubber band, a firm haptic confirms the pull has armed the swipe,
 * and releasing while armed runs it. An App Widget that takes the drag never pulls Home. A hidden
 * Home whose touches are consumed never reacts.
 */
@Composable
fun Modifier.homeSwipes(
    isSwipeUpEnabled: Boolean,
    isSwipeDownEnabled: Boolean,
    widgetSwipeState: WidgetSwipeState,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
): Modifier {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val density: Float = LocalDensity.current.density
    val currentDensity: Float by rememberUpdatedState(density)
    val currentIsSwipeUpEnabled: Boolean by rememberUpdatedState(isSwipeUpEnabled)
    val currentIsSwipeDownEnabled: Boolean by rememberUpdatedState(isSwipeDownEnabled)
    val currentOnSwipeUp: () -> Unit by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown: () -> Unit by rememberUpdatedState(onSwipeDown)
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val rubberBand: HomeRubberBand = remember(widgetSwipeState) {
        HomeRubberBand(
            coroutineScope = coroutineScope,
            density = { currentDensity },
            isSwipeUpEnabled = { currentIsSwipeUpEnabled },
            isSwipeDownEnabled = { currentIsSwipeDownEnabled },
            isWidgetTakenSwipe = { widgetSwipeState.isWidgetTakenSwipe },
            onArmed = { hapticFeedback.performHomeSwipeHaptic() },
            onSwipeUp = { currentOnSwipeUp() },
            onSwipeDown = { currentOnSwipeDown() }
        )
    }
    LaunchedEffect(isSwipeUpEnabled, isSwipeDownEnabled) {
        if (!rubberBand.isEnabled(rubberBand.pullPx)) {
            rubberBand.reset()
        }
    }
    LaunchedEffect(rubberBand) {
        snapshotFlow { widgetSwipeState.isWidgetTakenSwipe }
            .filter { isTaken -> isTaken }
            .collect { rubberBand.settle() }
    }
    return this
        .offset { IntOffset(x = 0, y = rubberBand.offsetPx.roundToInt()) }
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
