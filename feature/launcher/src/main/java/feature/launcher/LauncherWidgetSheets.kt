package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowsAltV
import compose.icons.fontawesomeicons.solid.PlusSquare
import core.ui.R as CoreUiR
import core.ui.composables.SheetActionRow
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetTextTitle
import core.ui.composables.sheetContainerColor
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetMenu
import feature.launcher.widgets.WidgetMenuAction
import feature.launcher.widgets.WidgetMenuState

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPlacementSheet(canAddToStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor
    ) {
        AddPlacementContent(canAddToStack = canAddToStack, onBelow = onBelow, onIntoStack = onIntoStack)
    }
}

/** The Add widget sheet's placement choices, on the sheet's container colour. */
@Composable
internal fun AddPlacementContent(canAddToStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTextTitle(title = stringResource(CoreUiR.string.add_widget))
        SheetActionRow(
            imageVector = FontAwesomeIcons.Solid.ArrowsAltV,
            label = stringResource(R.string.add_below),
            onClick = onBelow,
            supportingText = stringResource(R.string.add_below_supporting_text)
        )
        if (canAddToStack) {
            SheetActionRow(
                imageVector = FontAwesomeIcons.Solid.PlusSquare,
                label = stringResource(CoreUiR.string.add_next_to),
                onClick = onIntoStack,
                supportingText = stringResource(R.string.add_next_to_supporting_text)
            )
        }
    }
}
