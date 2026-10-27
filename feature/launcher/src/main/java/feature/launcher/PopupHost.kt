package feature.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import core.data.launchables.model.Launchable
import core.ui.util.consumeTouches
import feature.launcher.widgets.ResizeState
import kotlin.math.roundToInt

private const val ENTER_DURATION_MILLIS: Int = 200
private const val EXIT_DURATION_MILLIS: Int = 150
private const val ENTER_START_SCALE: Float = 0.92f
private const val EXIT_END_SCALE: Float = 0.96f
private val emphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Draws the outside-tap layer and the one open Pop-up above the launcher. */
@Composable
fun PopupHost(viewModel: PopupCardViewModel, onLaunch: (Launchable) -> Unit, onAddItems: () -> Unit, modifier: Modifier = Modifier) {
    val density: Density = LocalDensity.current
    val isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
    val popupState: PopupUiState by viewModel.popupState.collectAsState()
    val resizeState: ResizeState? by viewModel.resizeState.collectAsState()
    val expandedNotificationKeys: Set<String> by viewModel.expandedNotificationKeys.collectAsState()
    val sentNotificationKeys: Set<String> by viewModel.sentNotificationKeys.collectAsState()
    val notificationActions: PopupNotificationActions = remember(viewModel) {
        PopupNotificationActions(
            onOpen = { snapshot -> viewModel.onOpenNotification(key = snapshot.key) },
            onDismiss = { snapshot -> viewModel.onDismissNotification(key = snapshot.key) },
            onToggleExpanded = { snapshot -> viewModel.toggleNotificationExpanded(key = snapshot.key) },
            onAction = { snapshot, index -> viewModel.runNotificationAction(key = snapshot.key, index = index) },
            onReply = { snapshot, index, reply ->
                viewModel.runNotificationAction(key = snapshot.key, index = index, replyText = reply)
            }
        )
    }
    val openState: PopupUiState.Open? = popupState as? PopupUiState.Open
    var shownState: PopupUiState.Open? by remember { mutableStateOf(null) }
    var isExiting: Boolean by remember { mutableStateOf(false) }
    var cardSize: IntSize by remember { mutableStateOf(IntSize.Zero) }
    val progress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    val isOpen: Boolean = openState != null
    SideEffect {
        if (openState != null) {
            shownState = openState
        }
    }
    LaunchedEffect(isOpen, openState?.owner?.key) {
        if (isOpen) {
            isExiting = false
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ENTER_DURATION_MILLIS, easing = emphasizedDecelerate))
        } else if (shownState != null) {
            isExiting = true
            progress.animateTo(0f, tween(EXIT_DURATION_MILLIS))
            shownState = null
            isExiting = false
        }
    }
    val displayState: PopupUiState.Open? = openState ?: shownState
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val window: IntRect = IntRect(left = 0, top = 0, right = constraints.maxWidth, bottom = constraints.maxHeight)
        val windowHeightDp: Int = with(density) { window.height.toDp().value.roundToInt() }
        LaunchedEffect(windowHeightDp) {
            viewModel.setWindowHeight(heightDp = windowHeightDp)
        }
        val safePadding: PaddingValues = WindowInsets.safeDrawing.asPaddingValues()
        val safeInsets: PopupInsets = PopupInsets(
            top = with(density) { safePadding.calculateTopPadding().roundToPx() },
            bottom = with(density) { safePadding.calculateBottomPadding().roundToPx() }
        )
        val frame: PopupFrame? = displayState?.let { state ->
            PopupPlacement.place(
                anchor = state.anchor,
                window = window,
                safeInsets = safeInsets,
                contentHeightPx = cardSize.height,
                density = density.density,
                isRtl = isRtl
            )
        }
        if (isOpen || shownState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeTouches(onGestureEnd = if (resizeState != null) viewModel::finishResize else viewModel::close)
            )
        }
        if (displayState != null && frame != null) {
            val anchor: PopupAnchor? = displayState.anchor
            val pivotX: Float = if (cardSize.width == 0 || anchor == null) {
                0.5f
            } else {
                ((anchor.iconCenter.x - frame.offsetX) / cardSize.width).coerceIn(0f, 1f)
            }
            val pivotY: Float = if (cardSize.height == 0 || anchor == null) {
                0.5f
            } else {
                ((anchor.iconCenter.y - frame.offsetY) / cardSize.height).coerceIn(0f, 1f)
            }
            val headerClick: (() -> Unit)? = displayState.owner.appOwner?.let { app -> { onLaunch(app) } }
            LaunchedEffect(frame.width) {
                val widgetWidthDp: Int = with(density) {
                    (frame.width.toDp() - popupWidgetHorizontalPadding * 2).value.roundToInt()
                }
                viewModel.setCardWidth(widthDp = widgetWidthDp)
            }
            PopupCard(
                content = displayState.content,
                widget = displayState.widget,
                nowMillis = displayState.nowMillis,
                resize = resizeState,
                expandedNotificationKeys = expandedNotificationKeys,
                sentNotificationKeys = sentNotificationKeys,
                notificationActions = notificationActions,
                onHeaderClick = headerClick,
                onShortcutClick = { shortcut -> onLaunch(shortcut) },
                onItemClick = { item -> onLaunch(item.launchable) },
                onItemLongClick = { item -> viewModel.openItemMenu(item) },
                onAddItems = onAddItems,
                onRequestQuietModeOff = { viewModel.unpauseOwnerProfile(owner = displayState.owner) },
                onWidgetLongPress = viewModel::openWidgetMenu,
                onWidgetUnpause = viewModel::unpauseWorkProfile,
                onWidgetPageChanged = viewModel::onPageChanged,
                onResizeDrag = viewModel::dragResize,
                onResizeReset = viewModel::resetResize,
                onResizeDone = viewModel::finishResize,
                onResizeTap = viewModel::finishResize,
                onClose = viewModel::close,
                onLongPress = viewModel::openMenu,
                modifier = Modifier
                    .offset { IntOffset(x = frame.offsetX, y = frame.offsetY) }
                    .width(with(density) { frame.width.toDp() })
                    .heightIn(max = with(density) { frame.maxHeight.toDp() })
                    .onSizeChanged { size -> cardSize = size }
                    .graphicsLayer {
                        alpha = progress.value
                        val scale: Float = if (isExiting) {
                            1f - (1f - progress.value) * (1f - EXIT_END_SCALE)
                        } else {
                            ENTER_START_SCALE + progress.value * (1f - ENTER_START_SCALE)
                        }
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(pivotFractionX = pivotX, pivotFractionY = pivotY)
                    }
            )
        }
    }
}
