package feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import core.ui.R as CoreUiR
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
        dragHandle = { LauncherSheetDragHandle() },
        onDismissRequest = viewModel::closeMenu
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
        dragHandle = { LauncherSheetDragHandle() },
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(CoreUiR.string.add_widget),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            PlacementChoice(
                label = stringResource(R.string.add_below),
                supportingText = stringResource(R.string.add_below_supporting_text),
                onClick = onBelow
            )
            if (canAddToStack) {
                PlacementChoice(
                    label = stringResource(R.string.add_next_to),
                    supportingText = stringResource(R.string.add_next_to_supporting_text),
                    onClick = onIntoStack
                )
            }
        }
    }
}

@Composable
private fun PlacementChoice(label: String, supportingText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
        Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
    }
}
