package feature.launcher

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import core.ui.util.performSegmentTickHaptic
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val RELEASE_ANIMATION_MILLIS: Int = 150

/**
 * Lets a row be swiped toward the Rail to open its Pop-up. The row content
 * follows the finger, a tick plays at the open threshold and the content
 * returns to rest when the gesture ends. A swipe that reaches the Rail's
 * [railBounds] opens nothing.
 */
@Composable
fun Modifier.popupSwipe(
    enabled: Boolean,
    railBounds: () -> Rect?,
    isSwipeBlocked: () -> Boolean = { false },
    onSwipeOpen: () -> Unit
): Modifier {
    val density: Float = LocalDensity.current.density
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
    var rowOffset: Float by remember { mutableStateOf(0f) }
    var rowPositionInRoot: Offset by remember { mutableStateOf(Offset.Zero) }
    var releaseJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val currentOnSwipeOpen: () -> Unit by rememberUpdatedState(onSwipeOpen)
    val currentIsSwipeBlocked: () -> Boolean by rememberUpdatedState(isSwipeBlocked)
    val currentRailBounds: () -> Rect? by rememberUpdatedState(railBounds)
    val thresholdPx: Float = SwipeToOpen.thresholdPx(density = density)
    val velocityThresholdPx: Float = SwipeToOpen.velocityThresholdPx(density = density)
    val animateToRest: () -> Job = {
        releaseJob?.cancel()
        coroutineScope.launch {
            animate(initialValue = rowOffset, targetValue = 0f, animationSpec = tween(RELEASE_ANIMATION_MILLIS)) { value, _ ->
                rowOffset = value
            }
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            releaseJob?.cancel()
            rowOffset = 0f
        }
    }
    return this
        .onGloballyPositioned { coordinates -> rowPositionInRoot = coordinates.localToRoot(Offset.Zero) }
        .pointerInput(enabled, isRtl, density) {
            if (!enabled) {
                return@pointerInput
            }
            awaitEachGesture {
                val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
                val velocityTracker: VelocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var totalDx: Float = 0f
                var totalDy: Float = 0f
                var claim: SwipeClaim = SwipeClaim.Pending
                var wasPastThreshold: Boolean = false
                var hasReachedRail: Boolean = SwipeToOpen.hasReachedRail(
                    wasReached = false,
                    fingerInRoot = rowPositionInRoot + down.position,
                    railBounds = currentRailBounds()
                )
                var isCanceled: Boolean = false
                var isFinished: Boolean = false
                releaseJob?.cancel()
                while (!isFinished && !isCanceled) {
                    val event: PointerEvent = awaitPointerEvent()
                    val change: PointerInputChange =
                        event.changes.firstOrNull { pointerChange -> pointerChange.id == down.id } ?: break
                    if (currentIsSwipeBlocked() || change.isConsumed) {
                        isCanceled = true
                        break
                    }
                    hasReachedRail = SwipeToOpen.hasReachedRail(
                        wasReached = hasReachedRail,
                        fingerInRoot = rowPositionInRoot + change.position,
                        railBounds = currentRailBounds()
                    )
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (change.pressed) {
                        val delta: Offset = change.position - change.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y
                        if (claim == SwipeClaim.Pending) {
                            claim = SwipeToOpen.onDrag(dx = totalDx, dy = totalDy, isRtl = isRtl, slopPx = viewConfiguration.touchSlop)
                        }
                        if (claim == SwipeClaim.Claimed) {
                            change.consume()
                            rowOffset = swipeOffset(dx = totalDx, isRtl = isRtl, density = density)
                            val isPastThreshold: Boolean = directionalDx(dx = totalDx, isRtl = isRtl) >= thresholdPx
                            if (isPastThreshold != wasPastThreshold) {
                                if (isPastThreshold) {
                                    hapticFeedback.performSegmentTickHaptic()
                                }
                                wasPastThreshold = isPastThreshold
                            }
                        }
                    } else {
                        isFinished = true
                        val isOppositeHorizontal: Boolean = claim == SwipeClaim.Rejected && isHorizontalDominant(totalDx, totalDy)
                        if (isOppositeHorizontal) {
                            change.consume()
                        }
                        if (claim == SwipeClaim.Claimed) {
                            val velocityX: Float = velocityTracker.calculateVelocity().x
                            val shouldOpen: Boolean = SwipeToOpen.onRelease(
                                dx = totalDx,
                                velocityX = velocityX,
                                isRtl = isRtl,
                                thresholdPx = thresholdPx,
                                velocityThresholdPx = velocityThresholdPx
                            )
                            releaseJob = animateToRest()
                            if (shouldOpen && !hasReachedRail) {
                                currentOnSwipeOpen()
                            }
                        }
                    }
                }
                if (isCanceled) {
                    releaseJob = animateToRest()
                }
            }
        }
        .graphicsLayer { translationX = rowOffset }
}

private fun swipeOffset(dx: Float, isRtl: Boolean, density: Float): Float {
    val directionalOffset: Float = SwipeToOpen.offsetFor(dxPx = directionalDx(dx = dx, isRtl = isRtl), density = density)
    return if (isRtl) -directionalOffset else directionalOffset
}

private fun isHorizontalDominant(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)
