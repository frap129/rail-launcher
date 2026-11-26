package feature.launcher.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import core.data.launchables.model.Launchable
import core.ui.util.performLongPressHaptic
import core.ui.widgets.MediaPlayerUiState
import core.ui.widgets.ResizeState
import core.ui.widgets.WidgetAreaActions
import core.ui.widgets.WidgetAreaState
import core.ui.widgets.WidgetAreaUi
import core.ui.widgets.WidgetEditDraft
import core.ui.widgets.runHeaderTapAction

/** Collects the Widget Area and builds its Home actions. */
@Composable
fun rememberWidgetAreaUi(
    viewModel: WidgetAreaViewModel,
    onLaunch: (Launchable) -> Unit,
    onOpenAgenda: () -> Unit,
    menuEnabled: Boolean = true
): WidgetAreaUi {
    val state: WidgetAreaState by viewModel.widgetAreaState.collectAsState()
    val resize: ResizeState? by viewModel.resizeState.collectAsState()
    val editDraft: WidgetEditDraft? by viewModel.editState.collectAsState()
    val mediaPlayer: MediaPlayerUiState by viewModel.mediaPlayerState.collectAsState()
    val scrollToPlaceId: Long? by viewModel.scrollToPlaceId.collectAsState()
    val context: Context = LocalContext.current
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val actions: WidgetAreaActions = remember(viewModel, onLaunch, onOpenAgenda, menuEnabled, context, hapticFeedback) {
        WidgetAreaActions(
            menuEnabled = menuEnabled,
            onHomeHeight = viewModel::setHomeHeight,
            onWidgetAreaWidth = viewModel::setWidgetAreaWidth,
            onTapTarget = { target ->
                context.runHeaderTapAction(target = target, onLaunch = onLaunch, onOpenAgenda = onOpenAgenda)
            },
            onHeaderLongPress = { widgetId ->
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openHeaderMenu(widgetId = widgetId)
                }
            },
            onHeaderHeight = viewModel::setHeaderHeight,
            onAppWidgetLongPress = { widgetId ->
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openAppWidgetMenu(widgetId = widgetId)
                }
            },
            onAppWidgetUnpause = viewModel::unpauseWorkProfile,
            onPageSettled = viewModel::onPageSettled,
            onEmptyAreaLongPress = {
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openEmptyAreaMenu()
                }
            },
            onFinishResize = viewModel::finishResize,
            onDragResize = viewModel::dragResize,
            onResetResize = viewModel::resetResize,
            onMovePlace = viewModel::moveWidgetPlace,
            onRemovePlace = viewModel::removeWidgetPlaceInEdit,
            onToggleExpand = viewModel::toggleStackExpanded,
            onMovePage = viewModel::movePage,
            onRemovePage = viewModel::removePageInEdit,
            onSplitPage = viewModel::splitPage,
            onMergePlace = viewModel::mergePlace,
            onMediaPlayerOpen = viewModel::openMediaPlayer,
            onMediaPlayerLongPress = {
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openMediaPlayerMenu()
                }
            },
            onMediaCommand = viewModel::onMediaCommand,
            onScrolledToPlace = viewModel::onScrolledToPlace
        )
    }
    return remember(state, resize, editDraft, mediaPlayer, scrollToPlaceId, actions) {
        WidgetAreaUi(
            state = state,
            resize = resize,
            editDraft = editDraft,
            mediaPlayer = mediaPlayer,
            scrollToPlaceId = scrollToPlaceId,
            actions = actions
        )
    }
}
