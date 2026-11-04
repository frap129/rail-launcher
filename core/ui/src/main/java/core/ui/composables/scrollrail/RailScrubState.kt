package core.ui.composables.scrollrail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.ui.GroupMark
import core.ui.util.performRailTickHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val NO_SELECTED_MARK: Int = -1
private const val BEND_STRENGTH_RESTING: Float = 0f
private const val BEND_STRENGTH_ACTIVE: Float = 1f
private const val MIN_PEAK_DP: Float = 150f
private val markHeightDp: Dp = 20.dp
private val highlightSizeDp: Dp = 42.dp
private val bendSpring: SpringSpec<Offset> = spring(stiffness = Spring.StiffnessMediumLow)
private val bendStrengthSpring: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
private val bubbleSpring: SpringSpec<Offset> = spring()

internal class RailMetrics(density: Float) {
    val markHeight: Dp = markHeightDp
    val highlightSize: Dp = highlightSizeDp
    val markHeightPx: Float = markHeight.value * density
    val minPeakPx: Float = MIN_PEAK_DP * density
    val highlightSizePx: Float = highlightSize.value * density
    val railWidthPx: Float = railWidth.value * density
}

private class RailPointerAnimation {
    private val bendAnimation: Animatable<Offset, *> = Animatable(Offset.Zero, Offset.VectorConverter)
    private val bendStrengthAnimation: Animatable<Float, *> = Animatable(BEND_STRENGTH_RESTING, Float.VectorConverter)
    private val bubbleAnimation: Animatable<Offset, *> = Animatable(Offset.Zero, Offset.VectorConverter)
    val bendPosition: Offset get() = bendAnimation.value
    val bendStrength: Float get() = bendStrengthAnimation.value
    val bubblePosition: Offset get() = bubbleAnimation.value

    suspend fun start(at: Offset) {
        bendAnimation.snapTo(at)
        bubbleAnimation.snapTo(at)
        bendStrengthAnimation.animateTo(BEND_STRENGTH_ACTIVE, bendStrengthSpring)
    }

    suspend fun follow(at: Offset) {
        coroutineScope {
            launch { bendAnimation.animateTo(at, bendSpring) }
            launch { bubbleAnimation.animateTo(at, bubbleSpring) }
        }
    }

    suspend fun stop() {
        bendStrengthAnimation.animateTo(BEND_STRENGTH_RESTING, bendStrengthSpring)
    }
}

/**
 * Holds the Rail's scrub selection and the pointer animations that bend it.
 * The Rail's touch input feeds pointer positions into this state; ScrollRail
 * only reads it to draw the marks.
 */
@Stable
class RailScrubState internal constructor(
    private val markCount: Int,
    enabled: Boolean,
    density: Float,
    private val hapticFeedback: HapticFeedback,
    private val scope: CoroutineScope,
    private val onScroll: (Int) -> Unit,
    private val onScrollEnded: (Int) -> Unit
) {
    internal val metrics: RailMetrics = RailMetrics(density = density)
    internal val isEnabled: Boolean = enabled

    /** The height of the drawn Rail's marks and touch column. */
    val railHeight: Dp = (metrics.markHeight.value * markCount).dp
    private val railHeightPx: Float = railHeight.value * density
    private val pointerAnimation: RailPointerAnimation = RailPointerAnimation()

    /** The mark under the pointer during a scrub, or -1 when no scrub is active. */
    var selectedMarkIndex: Int by mutableIntStateOf(NO_SELECTED_MARK)
        private set

    /** The vertical offset that keeps the Rail under the pointer. */
    var railOffset: Float by mutableFloatStateOf(0f)
        private set

    internal val bendPosition: Offset get() = pointerAnimation.bendPosition
    internal val bendStrength: Float get() = pointerAnimation.bendStrength
    internal val bubblePosition: Offset get() = pointerAnimation.bubblePosition

    internal fun handlePointerEvent(eventType: PointerEventType, pointer: Offset) {
        if (eventType == PointerEventType.Press) {
            railOffset = 0f
        }
        railOffset = clampRailOffset(pointerY = pointer.y, railOffset = railOffset, railHeight = railHeightPx)
        val markIndex: Int = findRailMarkIndex(
            verticalOffset = pointer.y - railOffset,
            markHeightPx = metrics.markHeightPx,
            markCount = markCount
        )
        when (eventType) {
            PointerEventType.Press -> startScrub(pointer = pointer, markIndex = markIndex)
            PointerEventType.Move -> moveScrub(pointer = pointer, markIndex = markIndex)
            PointerEventType.Unknown, PointerEventType.Release -> endScrub(markIndex = markIndex)
        }
    }

    private fun startScrub(pointer: Offset, markIndex: Int) {
        hapticFeedback.performRailTickHaptic()
        onScroll(markIndex)
        selectedMarkIndex = markIndex
        scope.launch { pointerAnimation.start(at = pointer) }
    }

    private fun moveScrub(pointer: Offset, markIndex: Int) {
        scope.launch { pointerAnimation.follow(at = pointer) }
        if (markIndex != selectedMarkIndex) {
            hapticFeedback.performRailTickHaptic()
            onScroll(markIndex)
            selectedMarkIndex = markIndex
        }
    }

    private fun endScrub(markIndex: Int) {
        onScrollEnded(markIndex)
        selectedMarkIndex = NO_SELECTED_MARK
        scope.launch { pointerAnimation.stop() }
    }
}

/** Creates the Rail's scrub state, keeping the latest callbacks without restarting a scrub. */
@Composable
fun rememberRailScrubState(
    items: List<GroupMark>,
    onScroll: (Int) -> Unit,
    onScrollEnded: (Int) -> Unit,
    enabled: Boolean = true
): RailScrubState {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val density: Float = LocalDensity.current.density
    val currentOnScroll: State<(Int) -> Unit> = rememberUpdatedState(onScroll)
    val currentOnScrollEnded: State<(Int) -> Unit> = rememberUpdatedState(onScrollEnded)
    return remember(items.size, enabled, density) {
        RailScrubState(
            markCount = items.size,
            enabled = enabled,
            density = density,
            hapticFeedback = hapticFeedback,
            scope = scope,
            onScroll = { index -> currentOnScroll.value(index) },
            onScrollEnded = { index -> currentOnScrollEnded.value(index) }
        )
    }
}

/**
 * Feeds pointer positions into [state]. When [mirrorWidth] is set, the pointer
 * is mirrored horizontally within that width so a strip on the opposite edge
 * scrubs the Rail as if the finger were the same distance in from the Rail's
 * own edge. Leave [mirrorWidth] null to feed positions as-is.
 */
fun Modifier.railScrubInput(state: RailScrubState, mirrorWidth: Dp? = null): Modifier = pointerInput(state, mirrorWidth) {
    if (!state.isEnabled) {
        return@pointerInput
    }
    val mirrorWidthPx: Float? = mirrorWidth?.toPx()
    awaitPointerEventScope {
        while (true) {
            val event: PointerEvent = awaitPointerEvent()
            val position: Offset = event.changes.first().position
            state.handlePointerEvent(
                eventType = event.type,
                pointer = mirrorWidthPx?.let { width -> Offset(x = width - position.x, y = position.y) } ?: position
            )
        }
    }
}

private fun clampRailOffset(pointerY: Float, railOffset: Float, railHeight: Float): Float = when {
    pointerY < railOffset -> pointerY
    pointerY > railOffset + railHeight -> pointerY - railHeight
    else -> railOffset
}

private fun findRailMarkIndex(verticalOffset: Float, markHeightPx: Float, markCount: Int): Int = if (markCount == 0) {
    0
} else {
    (verticalOffset / markHeightPx).toInt().coerceIn(0, markCount - 1)
}
