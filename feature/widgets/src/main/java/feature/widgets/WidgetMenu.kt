package feature.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import compose.icons.fontawesomeicons.solid.ArrowsAltV
import compose.icons.fontawesomeicons.solid.BatteryThreeQuarters
import compose.icons.fontawesomeicons.solid.Calendar
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.Font
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.PlusSquare
import compose.icons.fontawesomeicons.solid.SlidersH
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.prefs.ClockStyle
import core.ui.R as CoreUiR

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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = state.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = state.label,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
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
private fun WidgetMenuRow(
    item: WidgetMenuItem,
    onToggle: (WidgetMenuAction, Boolean) -> Unit,
    onAction: (WidgetMenuAction) -> Unit,
    onChoiceClick: (WidgetMenuAction) -> Unit,
    onChoiceSelected: (WidgetMenuAction, WidgetMenuChoiceKey) -> Unit
) {
    when (item) {
        is WidgetMenuItem.Toggle -> ToggleRow(item = item, onToggle = onToggle)
        is WidgetMenuItem.Action -> ActionRow(item = item, onAction = onAction)
        is WidgetMenuItem.Choice -> ChoiceRow(item = item, onChoiceClick = onChoiceClick, onChoiceSelected = onChoiceSelected)
    }
}

@Composable
private fun ToggleRow(item: WidgetMenuItem.Toggle, onToggle: (WidgetMenuAction, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = item.checked,
                enabled = item.enabled,
                role = Role.Switch,
                onValueChange = { checked -> onToggle(item.id, checked) }
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetMenuIcon(action = item.id)
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = stringResource(item.id.label()), style = MaterialTheme.typography.titleLarge)
            item.supportingText?.let { supportingText ->
                Text(text = stringResource(supportingText), style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = item.checked, onCheckedChange = null, enabled = item.enabled)
    }
}

@Composable
private fun ActionRow(item: WidgetMenuItem.Action, onAction: (WidgetMenuAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(item.id) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetMenuIcon(action = item.id)
        Text(text = stringResource(item.id.label()), style = MaterialTheme.typography.titleLarge)
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
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
                            role = Role.RadioButton,
                            onClick = { onChoiceSelected(item.id, option.key) }
                        )
                        .padding(start = 76.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option.key == item.selectedKey, onClick = null)
                    Text(text = stringResource(option.label), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetMenuIcon(action: WidgetMenuAction) {
    Icon(
        modifier = Modifier.size(56.dp).padding(10.dp),
        imageVector = action.icon(),
        contentDescription = null
    )
}

private fun WidgetMenuAction.label(): Int = when (this) {
    WidgetMenuAction.Clock -> R.string.widget_clock
    WidgetMenuAction.Date -> R.string.widget_date
    WidgetMenuAction.Battery -> CoreUiR.string.battery
    WidgetMenuAction.UpcomingEvents -> R.string.upcoming_events
    WidgetMenuAction.ClockStyle -> R.string.clock_style
    WidgetMenuAction.AddWidget -> R.string.add_widget
    WidgetMenuAction.Resize -> R.string.resize_widget
    WidgetMenuAction.Reconfigure -> R.string.reconfigure_widget
    WidgetMenuAction.RemoveHeader -> R.string.remove_header
    WidgetMenuAction.RestoreHeader -> R.string.restore_header
    WidgetMenuAction.RemoveWidget -> R.string.remove_widget
    WidgetMenuAction.EditWidgets -> R.string.edit_widgets
    WidgetMenuAction.WidgetSettings -> R.string.widget_settings
}

private fun WidgetMenuAction.icon(): ImageVector = when (this) {
    WidgetMenuAction.Clock -> FontAwesomeIcons.Solid.Clock
    WidgetMenuAction.Date -> FontAwesomeIcons.Solid.Calendar
    WidgetMenuAction.Battery -> FontAwesomeIcons.Solid.BatteryThreeQuarters
    WidgetMenuAction.UpcomingEvents -> FontAwesomeIcons.Solid.Calendar
    WidgetMenuAction.ClockStyle -> FontAwesomeIcons.Solid.Font
    WidgetMenuAction.AddWidget -> FontAwesomeIcons.Solid.PlusSquare
    WidgetMenuAction.Resize -> FontAwesomeIcons.Solid.ArrowsAltV
    WidgetMenuAction.Reconfigure -> FontAwesomeIcons.Solid.Cog
    WidgetMenuAction.RemoveHeader -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.RestoreHeader -> FontAwesomeIcons.Solid.Th
    WidgetMenuAction.RemoveWidget -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.EditWidgets -> FontAwesomeIcons.Solid.GripVertical
    WidgetMenuAction.WidgetSettings -> FontAwesomeIcons.Solid.SlidersH
}
