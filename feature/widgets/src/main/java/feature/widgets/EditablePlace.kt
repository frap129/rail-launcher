package feature.widgets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.MinusCircle
import core.ui.composables.DragHandle
import core.ui.composables.OutlinedText
import core.ui.layout.dragHandleRailGap
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import core.ui.util.performSegmentTickHaptic
import sh.calvin.reorderable.ReorderableColumn

private val editablePlaceShape = RoundedCornerShape(12.dp)
private val editablePlaceOutlineWidth = 2.dp
private val editablePlacePadding = 8.dp
private const val EDIT_SCRIM_ALPHA: Float = 0.40f
private val removeControlSize = 48.dp
private val removeIconSize = 32.dp
private val topBandHeight = 160.dp
private const val DRAG_SCALE: Float = 1.02f
private val DRAG_ELEVATION = 8.dp
private const val LIFT_ANIMATION_MILLIS: Int = 120

/** The reorderable list of Widget places shown during Edit Widgets. */
@Composable
internal fun EditableWidgetArea(
    places: List<WidgetPlaceModel>,
    onMovePlace: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemovePlace: (Long) -> Unit,
    placeContent: @Composable (WidgetPlaceModel, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    ReorderableColumn(
        list = places,
        onSettle = onMovePlace,
        onMove = { hapticFeedback.performSegmentTickHaptic() },
        modifier = modifier.animateContentSize()
    ) { index, place, isDragging ->
        key(place.id) {
            ReorderableItem {
                EditablePlace(
                    place = place,
                    isDragging = isDragging,
                    onRemove = { onRemovePlace(place.id) },
                    onMoveUp = { onMovePlace(index, index - 1) },
                    onMoveDown = { onMovePlace(index, index + 1) },
                    handleModifier = Modifier.draggableHandle(
                        onDragStarted = { hapticFeedback.performLongPressHaptic() }
                    )
                ) { contentModifier ->
                    placeContent(place, contentModifier)
                }
            }
        }
    }
}

@Composable
private fun EditablePlace(
    place: WidgetPlaceModel,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val outlineColor: Color = MaterialTheme.colorScheme.outline
    val scrimColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = EDIT_SCRIM_ALPHA)
    val label: String = when (val widget = place.widgets.firstOrNull()) {
        is HeaderModel -> stringResource(R.string.header_title)
        is AppWidgetModel -> widget.label
        null -> stringResource(R.string.widget_title)
    }
    val removeLabel: String = stringResource(R.string.remove_widget_named, label)
    val moveUpLabel: String = stringResource(R.string.move_up)
    val moveDownLabel: String = stringResource(R.string.move_down)
    val reorderLabel: String = stringResource(R.string.reorder_widget, label)
    val elevation: Dp by animateDpAsState(
        targetValue = if (isDragging) DRAG_ELEVATION else 0.dp,
        animationSpec = tween(durationMillis = LIFT_ANIMATION_MILLIS)
    )
    val scale: Float by animateFloatAsState(
        targetValue = if (isDragging) DRAG_SCALE else 1f,
        animationSpec = tween(durationMillis = LIFT_ANIMATION_MILLIS)
    )
    var placeHeightPx: Int by remember { mutableIntStateOf(0) }
    val density: Density = LocalDensity.current
    val placeHeightDp: Dp = with(density) { placeHeightPx.toDp() }
    val isTall: Boolean = placeHeightDp > topBandHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> placeHeightPx = size.height }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                shape = editablePlaceShape
                clip = false
            }
            .border(width = editablePlaceOutlineWidth, color = outlineColor, shape = editablePlaceShape)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = moveUpLabel) {
                        onMoveUp()
                        true
                    },
                    CustomAccessibilityAction(label = moveDownLabel) {
                        onMoveDown()
                        true
                    },
                    CustomAccessibilityAction(label = removeLabel) {
                        onRemove()
                        true
                    }
                )
            }
    ) {
        content(Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = scrimColor, shape = editablePlaceShape)
                .consumeTouches()
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = editablePlacePadding, top = editablePlacePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(removeControlSize)
                    .background(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape)
                    .clickable(onClick = onRemove)
                    .semantics { contentDescription = removeLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.MinusCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(removeIconSize)
                )
            }
            OutlinedText(
                text = label,
                modifier = Modifier
                    .padding(start = editablePlacePadding, end = editablePlacePadding)
                    .weight(1f),
                style = MaterialTheme.typography.labelLarge
            )
        }
        val handleTopPadding: Dp = if (isTall) {
            (topBandHeight - removeControlSize) / 2
        } else {
            0.dp
        }
        DragHandle(
            modifier = handleModifier
                .align(if (isTall) Alignment.TopEnd else Alignment.CenterEnd)
                .padding(top = handleTopPadding, end = dragHandleRailGap),
            contentDescription = reorderLabel
        )
    }
}
