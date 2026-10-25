package feature.launcher.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowsAltH
import compose.icons.fontawesomeicons.solid.ArrowsAltV
import compose.icons.fontawesomeicons.solid.Ban
import compose.icons.fontawesomeicons.solid.BatteryThreeQuarters
import compose.icons.fontawesomeicons.solid.Calendar
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.ExchangeAlt
import compose.icons.fontawesomeicons.solid.Font
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.Music
import compose.icons.fontawesomeicons.solid.Play
import compose.icons.fontawesomeicons.solid.PlusSquare
import compose.icons.fontawesomeicons.solid.SlidersH
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.icons.model.Icon
import core.data.prefs.ClockStyle
import core.ui.R as CoreUiR
import core.ui.composables.SheetSwitchRow
import core.ui.composables.sheetRowHorizontalPadding
import core.ui.composables.sheetRowIconPadding
import core.ui.composables.sheetRowIconSize
import core.ui.composables.sheetRowVerticalPadding
import core.widgets.HeaderClockStyleChooser
import feature.launcher.R

/** The content of the Widget Area's context sheet. */
@Composable
fun WidgetMenu(
    state: WidgetMenuState,
    onToggle: (WidgetMenuAction, Boolean) -> Unit,
    onAction: (WidgetMenuAction) -> Unit,
    onChoiceClick: (WidgetMenuAction) -> Unit,
    onChoiceSelected: (WidgetMenuAction, WidgetMenuChoiceKey) -> Unit,
    onClockStyleSelected: (ClockStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        WidgetMenuState.Closed -> Unit

        is WidgetMenuState.HeaderMenu -> Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.header_title),
                modifier = Modifier.padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding),
                style = MaterialTheme.typography.headlineMedium
            )
            state.items.forEach { item ->
                WidgetMenuRow(
                    item = item,
                    onToggle = onToggle,
                    onAction = onAction,
                    onChoiceClick = onChoiceClick,
                    onChoiceSelected = onChoiceSelected
                )
            }
        }

        is WidgetMenuState.EmptyAreaMenu -> Column(modifier = modifier.fillMaxWidth()) {
            state.items.forEach { item ->
                WidgetMenuRow(
                    item = item,
                    onToggle = onToggle,
                    onAction = onAction,
                    onChoiceClick = onChoiceClick,
                    onChoiceSelected = onChoiceSelected
                )
            }
        }

        is WidgetMenuState.AppWidgetMenu -> Column(modifier = modifier.fillMaxWidth()) {
            WidgetMenuTitle(icon = state.icon, label = state.label)
            state.items.forEach { item ->
                WidgetMenuRow(
                    item = item,
                    onToggle = onToggle,
                    onAction = onAction,
                    onChoiceClick = onChoiceClick,
                    onChoiceSelected = onChoiceSelected
                )
            }
        }

        is WidgetMenuState.MediaPlayerMenu -> Column(modifier = modifier.fillMaxWidth()) {
            WidgetMenuTitle(icon = state.icon, label = state.label)
            state.items.forEach { item ->
                WidgetMenuRow(
                    item = item,
                    onToggle = onToggle,
                    onAction = onAction,
                    onChoiceClick = onChoiceClick,
                    onChoiceSelected = onChoiceSelected
                )
            }
        }

        is WidgetMenuState.ClockStyleChooser -> HeaderClockStyleChooser(
            selected = state.selected,
            previews = state.previews,
            onSelect = onClockStyleSelected,
            modifier = modifier
        )
    }
}

@Composable
private fun WidgetMenuTitle(icon: Icon, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun WidgetMenuRow(
    item: WidgetMenuItem,
    onToggle: (WidgetMenuAction, Boolean) -> Unit,
    onAction: (WidgetMenuAction) -> Unit,
    onChoiceClick: (WidgetMenuAction) -> Unit,
    onChoiceSelected: (WidgetMenuAction, WidgetMenuChoiceKey) -> Unit
) {
    when (item) {
        is WidgetMenuItem.Toggle -> ToggleRow(item = item, onToggle = onToggle, onAction = onAction)
        is WidgetMenuItem.Action -> ActionRow(item = item, onAction = onAction)
        is WidgetMenuItem.Choice -> ChoiceRow(item = item, onChoiceClick = onChoiceClick, onChoiceSelected = onChoiceSelected)
    }
}

@Composable
private fun ToggleRow(item: WidgetMenuItem.Toggle, onToggle: (WidgetMenuAction, Boolean) -> Unit, onAction: (WidgetMenuAction) -> Unit) {
    SheetSwitchRow(
        imageVector = item.id.icon(),
        label = stringResource(item.id.label()),
        checked = item.checked,
        onCheckedChange = { checked -> onToggle(item.id, checked) },
        supportingText = item.supportingText?.let { supportingText -> stringResource(supportingText) },
        enabled = item.enabled,
        onClick = if (item.rowOpensAction) ({ onAction(item.id) }) else null
    )
}

@Composable
private fun ActionRow(item: WidgetMenuItem.Action, onAction: (WidgetMenuAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(item.id) }
            .padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetMenuIcon(action = item.id)
        Text(
            text = item.labelArg?.let { arg -> stringResource(item.id.label(), arg) } ?: stringResource(item.id.label()),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun ChoiceRow(
    item: WidgetMenuItem.Choice,
    onChoiceClick: (WidgetMenuAction) -> Unit,
    onChoiceSelected: (WidgetMenuAction, WidgetMenuChoiceKey) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onChoiceClick(item.id) }
                .padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetMenuIcon(action = item.id)
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = stringResource(item.id.label()), style = MaterialTheme.typography.titleLarge)
                Text(text = stringResource(item.valueLabel), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (item.expanded) {
            item.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.key == item.selectedKey,
                            enabled = option.enabled,
                            role = Role.RadioButton,
                            onClick = { onChoiceSelected(item.id, option.key) }
                        )
                        .padding(start = 76.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option.key == item.selectedKey, onClick = null, enabled = option.enabled)
                    Text(text = stringResource(option.label), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetMenuIcon(action: WidgetMenuAction) {
    MaterialIcon(
        modifier = Modifier.size(sheetRowIconSize).padding(sheetRowIconPadding),
        imageVector = action.icon(),
        contentDescription = null
    )
}

private fun WidgetMenuAction.label(): Int = when (this) {
    WidgetMenuAction.EditFavorites -> R.string.edit_favorites
    WidgetMenuAction.Clock -> R.string.widget_clock
    WidgetMenuAction.Date -> R.string.widget_date
    WidgetMenuAction.Battery -> CoreUiR.string.battery
    WidgetMenuAction.UpcomingEvents -> CoreUiR.string.upcoming_events
    WidgetMenuAction.ClockStyle -> R.string.clock_style
    WidgetMenuAction.AddWidget -> CoreUiR.string.add_widget
    WidgetMenuAction.MoveWidgetArea -> R.string.move_widget_area
    WidgetMenuAction.Resize -> R.string.resize_widget
    WidgetMenuAction.Reconfigure -> R.string.reconfigure_widget
    WidgetMenuAction.RemoveHeader -> R.string.remove_header
    WidgetMenuAction.RemoveFromStack -> R.string.remove_from_stack
    WidgetMenuAction.RestoreHeader -> R.string.restore_header
    WidgetMenuAction.RemoveWidget -> CoreUiR.string.remove_widget
    WidgetMenuAction.ReplaceHeader -> R.string.replace_header
    WidgetMenuAction.ExtendUnderRail -> CoreUiR.string.extend_under_rail
    WidgetMenuAction.EditWidgets -> R.string.edit_widgets
    WidgetMenuAction.WidgetSettings -> R.string.widget_settings
    WidgetMenuAction.MediaPlayer -> CoreUiR.string.media_player
    WidgetMenuAction.OpenMediaPlayer -> R.string.open_app
    WidgetMenuAction.ExcludeMediaApp -> R.string.exclude_media_app
    WidgetMenuAction.MediaPlayerSettings -> R.string.media_player_settings
}

private fun WidgetMenuAction.icon(): ImageVector = when (this) {
    WidgetMenuAction.EditFavorites -> FontAwesomeIcons.Solid.GripVertical
    WidgetMenuAction.Clock -> FontAwesomeIcons.Solid.Clock
    WidgetMenuAction.Date -> FontAwesomeIcons.Solid.Calendar
    WidgetMenuAction.Battery -> FontAwesomeIcons.Solid.BatteryThreeQuarters
    WidgetMenuAction.UpcomingEvents -> FontAwesomeIcons.Solid.Calendar
    WidgetMenuAction.ClockStyle -> FontAwesomeIcons.Solid.Font
    WidgetMenuAction.AddWidget -> FontAwesomeIcons.Solid.PlusSquare
    WidgetMenuAction.MoveWidgetArea -> FontAwesomeIcons.Solid.ArrowsAltV
    WidgetMenuAction.Resize -> FontAwesomeIcons.Solid.ArrowsAltV
    WidgetMenuAction.Reconfigure -> FontAwesomeIcons.Solid.Cog
    WidgetMenuAction.RemoveHeader -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.RemoveFromStack -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.RestoreHeader -> FontAwesomeIcons.Solid.Th
    WidgetMenuAction.RemoveWidget -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.ReplaceHeader -> FontAwesomeIcons.Solid.ExchangeAlt
    WidgetMenuAction.ExtendUnderRail -> FontAwesomeIcons.Solid.ArrowsAltH
    WidgetMenuAction.EditWidgets -> FontAwesomeIcons.Solid.GripVertical
    WidgetMenuAction.WidgetSettings -> FontAwesomeIcons.Solid.SlidersH
    WidgetMenuAction.MediaPlayer -> FontAwesomeIcons.Solid.Music
    WidgetMenuAction.OpenMediaPlayer -> FontAwesomeIcons.Solid.Play
    WidgetMenuAction.ExcludeMediaApp -> FontAwesomeIcons.Solid.Ban
    WidgetMenuAction.MediaPlayerSettings -> FontAwesomeIcons.Solid.SlidersH
}
