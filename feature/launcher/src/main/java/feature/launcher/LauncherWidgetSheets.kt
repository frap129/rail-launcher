package feature.launcher

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import core.ui.composables.SheetDragHandle
import core.ui.composables.sheetContainerColor
import core.ui.widgets.WidgetMenu
import core.ui.widgets.WidgetMenuAction
import core.ui.widgets.WidgetMenuState
import feature.launcher.widgets.WidgetAreaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherWidgetMenu(viewModel: WidgetAreaViewModel) {
    val menuState: WidgetMenuState by viewModel.menuState.collectAsState()
    val state: WidgetMenuState = menuState
    if (state is WidgetMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = viewModel::closeMenu,
        containerColor = sheetContainerColor
    ) {
        WidgetMenu(
            state = state,
            onToggle = { action, checked ->
                when (action) {
                    WidgetMenuAction.Clock -> viewModel.setClockVisible(checked)

                    WidgetMenuAction.Date -> viewModel.setDateVisible(checked)

                    WidgetMenuAction.ExtendUnderRail -> (state as? WidgetMenuState.AppWidgetMenu)?.let { menu ->
                        viewModel.setExtendsUnderRail(widgetId = menu.widgetId, extendsUnderRail = checked)
                    }

                    WidgetMenuAction.Battery,
                    WidgetMenuAction.ClockStyle,
                    WidgetMenuAction.EditFavorites,
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.Resize,
                    WidgetMenuAction.Reconfigure,
                    WidgetMenuAction.ReplaceHeader,
                    WidgetMenuAction.RemoveHeader,
                    WidgetMenuAction.RemoveFromStack,
                    WidgetMenuAction.RestoreHeader,
                    WidgetMenuAction.RemoveWidget,
                    WidgetMenuAction.EditWidgets,
                    WidgetMenuAction.WidgetSettings,
                    WidgetMenuAction.OpenMediaPlayer,
                    WidgetMenuAction.ExcludeMediaApp,
                    WidgetMenuAction.MediaPlayerSettings -> Unit

                    WidgetMenuAction.MediaPlayer -> viewModel.setMediaPlayerEnabled(enabled = checked)

                    WidgetMenuAction.UpcomingEvents -> viewModel.setUpcomingEventsEnabled(checked)
                }
            },
            onChoiceClick = viewModel::toggleChoice,
            onChoiceSelected = viewModel::selectChoice,
            onAction = viewModel::onMenuAction,
            onClockStyleSelected = viewModel::selectClockStyle
        )
    }
}
