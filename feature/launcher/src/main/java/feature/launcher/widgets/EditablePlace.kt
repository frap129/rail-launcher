package feature.launcher.widgets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import compose.icons.fontawesomeicons.solid.ChevronDown
import compose.icons.fontawesomeicons.solid.ChevronUp
import compose.icons.fontawesomeicons.solid.ExternalLinkAlt
import compose.icons.fontawesomeicons.solid.MinusCircle
import core.ui.R as CoreUiR
import core.ui.composables.DragHandle
import core.ui.composables.OutlinedText
import core.ui.layout.dragHandleEndPadding
import core.ui.layout.minTouchTargetSize
import core.ui.util.performLongPressHaptic
import core.ui.util.performSegmentTickHaptic
import core.ui.widgets.AppWidgetModel
import core.ui.widgets.HeaderModel
import core.ui.widgets.WidgetModel
import feature.launcher.R
import sh.calvin.reorderable.ReorderableColumn

private val editablePlaceShape = RoundedCornerShape(12.dp)
private val editablePlaceOutlineWidth = 2.dp
private val editablePlacePadding = 8.dp
private const val EDIT_SCRIM_ALPHA: Float = 0.40f
private val removeIconSize = 32.dp
private val topBandHeight = 160.dp
private const val DRAG_SCALE: Float = 1.02f
private val DRAG_ELEVATION = 8.dp
private const val LIFT_ANIMATION_MILLIS: Int = 120
private const val MERGE_HOVER_MILLIS: Long = 400L

/** The shared drag state for merging one Widget Area place into another. */
@Stable
class EditMergeState {
    val bounds: MutableMap<Long, Rect> = mutableStateMapOf()
    var mergeSourceId: Long? by mutableStateOf(null)
    var mergeTargetId: Long? by mutableStateOf(null)
    var mergeHoverStartedAt: Long by mutableLongStateOf(0L)
    var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
}

/** One reorderable Widget place inside Edit Widgets. */
@Composable
internal fun EditablePlaceItem(
    place: WidgetPlaceModel,
    index: Int,
    places: List<WidgetPlaceModel>,
    mergeState: EditMergeState,
    isDragging: Boolean,
    isExpanded: Boolean,
    maxStackPages: Int,
    onMovePlace: (Int, Int) -> Unit,
    onRemovePlace: (Long) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onMovePage: (Long, Int, Int) -> Unit,
    onRemovePage: (Long, Long) -> Unit,
    onSplitPage: (Long, Long, Int) -> Unit,
    onMergePlace: (Long, Long) -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val mergeEligible: Boolean = place.widgets.size == 1

    fun updateMergeTarget(rootPosition: Offset) {
        val sourceId: Long = mergeState.mergeSourceId ?: return
        val candidate: Long? = mergeState.bounds.entries
            .firstOrNull { (placeId, rect) -> placeId != sourceId && rect.contains(rootPosition) }
            ?.key
        if (candidate != mergeState.mergeTargetId) {
            mergeState.mergeTargetId = candidate
            mergeState.mergeHoverStartedAt = System.currentTimeMillis()
        }
    }

    EditablePlace(
        place = place,
        isDragging = isDragging,
        isExpanded = isExpanded,
        isMergeTarget = mergeState.mergeTargetId == place.id,
        canMerge = mergeEligible,
        onRemove = { onRemovePlace(place.id) },
        onMoveUp = { onMovePlace(index, index - 1) },
        onMoveDown = { onMovePlace(index, index + 1) },
        onToggleExpand = { onToggleExpand(place.id) },
        onMovePage = { from, to -> onMovePage(place.id, from, to) },
        onRemovePage = { widgetId -> onRemovePage(place.id, widgetId) },
        onSplitPage = { widgetId -> onSplitPage(place.id, widgetId, index + 1) },
        onSplitAt = { widgetId, rootY ->
            val targetIndex: Int = places.indexOfFirst { candidate ->
                candidate.id != place.id &&
                    mergeState.bounds[candidate.id]?.contains(Offset(0f, rootY)) == true
            }.takeIf { target -> target >= 0 } ?: places.size
            onSplitPage(place.id, widgetId, targetIndex)
        },
        maxStackPages = maxStackPages,
        handleModifier = handleModifier,
        modifier = modifier.onGloballyPositioned { coordinates ->
            val topLeft: Offset = coordinates.localToRoot(Offset.Zero)
            mergeState.bounds[place.id] = Rect(
                offset = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    width = coordinates.size.width.toFloat(),
                    height = coordinates.size.height.toFloat()
                )
            )
            if (mergeState.mergeSourceId == place.id) {
                mergeState.sourceCoordinates = coordinates
            }
        },
        onMergeDragStart = {
            if (mergeEligible) {
                mergeState.mergeSourceId = place.id
                mergeState.mergeTargetId = null
                mergeState.mergeHoverStartedAt = 0L
            }
        },
        onMergeDrag = { position ->
            val coordinates: LayoutCoordinates = mergeState.sourceCoordinates ?: return@EditablePlace
            updateMergeTarget(coordinates.localToRoot(position))
        },
        onMergeDragEnd = {
            val sourceId: Long = mergeState.mergeSourceId ?: return@EditablePlace
            val targetId: Long = mergeState.mergeTargetId ?: return@EditablePlace
            if (System.currentTimeMillis() - mergeState.mergeHoverStartedAt >= MERGE_HOVER_MILLIS) {
                onMergePlace(sourceId, targetId)
            }
            mergeState.mergeSourceId = null
            mergeState.mergeTargetId = null
        },
        onMergeDragCancel = {
            mergeState.mergeSourceId = null
            mergeState.mergeTargetId = null
        }
    ) { contentModifier ->
        content(contentModifier)
    }
}

@Composable
private fun EditablePlace(
    place: WidgetPlaceModel,
    isDragging: Boolean,
    isExpanded: Boolean,
    isMergeTarget: Boolean,
    canMerge: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleExpand: () -> Unit,
    onMovePage: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemovePage: (widgetId: Long) -> Unit,
    onSplitPage: (widgetId: Long) -> Unit,
    onSplitAt: (widgetId: Long, rootY: Float) -> Unit,
    maxStackPages: Int,
    handleModifier: Modifier,
    onMergeDragStart: () -> Unit,
    onMergeDrag: (androidx.compose.ui.geometry.Offset) -> Unit,
    onMergeDragEnd: () -> Unit,
    onMergeDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val outlineColor: Color = if (isMergeTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val scrimColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = EDIT_SCRIM_ALPHA)
    val label: String = when (val widget = place.widgets.firstOrNull()) {
        is HeaderModel -> stringResource(R.string.header_title)
        is AppWidgetModel -> widget.label
        null -> stringResource(R.string.widget_title)
    }
    val removeLabel: String = stringResource(R.string.remove_widget_named, label)
    val moveUpLabel: String = stringResource(CoreUiR.string.move_up)
    val moveDownLabel: String = stringResource(CoreUiR.string.move_down)
    val reorderLabel: String = stringResource(R.string.reorder_widget, label)
    val expandLabel: String = stringResource(if (isExpanded) R.string.collapse_stack else R.string.expand_stack)
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
        if (isExpanded && place.isStack) {
            ExpandedStack(
                place = place,
                maxStackPages = maxStackPages,
                onMovePage = onMovePage,
                onRemovePage = onRemovePage,
                onSplitPage = onSplitPage,
                onSplitAt = onSplitAt,
                modifier = Modifier.padding(top = topBandHeight)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = scrimColor, shape = editablePlaceShape)
                .pointerInput(place.id, canMerge) {
                    if (canMerge) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onMergeDragStart() },
                            onDragEnd = { onMergeDragEnd() },
                            onDragCancel = { onMergeDragCancel() },
                            onDrag = { change, _ ->
                                change.consume()
                                onMergeDrag(change.position)
                            }
                        )
                    }
                }
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = editablePlacePadding, top = editablePlacePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(minTouchTargetSize)
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
            if (place.isStack) {
                Box(
                    modifier = Modifier
                        .size(minTouchTargetSize)
                        .clickable(onClick = onToggleExpand)
                        .semantics { contentDescription = expandLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) FontAwesomeIcons.Solid.ChevronUp else FontAwesomeIcons.Solid.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(removeIconSize)
                    )
                }
            }
        }
        val handleTopPadding: Dp = if (isTall) {
            (topBandHeight - minTouchTargetSize) / 2
        } else {
            0.dp
        }
        DragHandle(
            modifier = handleModifier
                .align(if (isTall) Alignment.TopEnd else Alignment.CenterEnd)
                .padding(top = handleTopPadding, end = dragHandleEndPadding),
            contentDescription = reorderLabel
        )
    }
}

@Composable
private fun ExpandedStack(
    place: WidgetPlaceModel,
    maxStackPages: Int,
    onMovePage: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemovePage: (widgetId: Long) -> Unit,
    onSplitPage: (widgetId: Long) -> Unit,
    onSplitAt: (widgetId: Long, rootY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val pages: List<WidgetModel> = place.widgets
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = editablePlacePadding)) {
        ReorderableColumn(
            list = pages,
            onSettle = onMovePage,
            onMove = { hapticFeedback.performSegmentTickHaptic() }
        ) { _, widget, _ ->
            key(widget.id) {
                ReorderableItem {
                    ExpandedPageRow(
                        widget = widget,
                        onRemove = { onRemovePage(widget.id) },
                        onSplit = { onSplitPage(widget.id) },
                        onSplitAt = onSplitAt,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { hapticFeedback.performLongPressHaptic() }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedPageRow(
    widget: WidgetModel,
    onRemove: () -> Unit,
    onSplit: () -> Unit,
    onSplitAt: (widgetId: Long, rootY: Float) -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val label: String = when (widget) {
        is HeaderModel -> stringResource(R.string.header_title)
        is AppWidgetModel -> widget.label
    }
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    var dragY: Float? by remember { mutableStateOf(null) }
    val removeLabel: String = stringResource(R.string.remove_from_stack_named, label)
    val splitLabel: String = stringResource(R.string.split_page_named, label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(minTouchTargetSize)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = removeLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.MinusCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
        OutlinedText(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = editablePlacePadding),
            style = MaterialTheme.typography.labelLarge
        )
        Box(
            modifier = Modifier
                .size(minTouchTargetSize)
                .onGloballyPositioned { layout -> coordinates = layout }
                .pointerInput(widget.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragY = null },
                        onDrag = { change, _ ->
                            change.consume()
                            dragY = coordinates?.localToRoot(change.position)?.y
                        },
                        onDragEnd = {
                            dragY?.let { y -> onSplitAt(widget.id, y) }
                            dragY = null
                        },
                        onDragCancel = { dragY = null }
                    )
                }
                .clickable(onClick = onSplit)
                .semantics { contentDescription = splitLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.ExternalLinkAlt,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        DragHandle(
            modifier = handleModifier.padding(end = editablePlacePadding),
            contentDescription = stringResource(R.string.reorder_stack_page, label)
        )
    }
}
