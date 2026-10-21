package feature.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Calendar
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.Font
import compose.icons.fontawesomeicons.solid.SlidersH
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.prefs.ClockStyle

/** The content of the Widget Area's context sheet. */
@Composable
fun WidgetMenu(
    state: WidgetMenuState,
    onToggle: (WidgetMenuAction, Boolean) -> Unit,
    onAction: (WidgetMenuAction) -> Unit,
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
                WidgetMenuRow(item = item, onToggle = onToggle, onAction = onAction)
            }
        }

        is WidgetMenuState.EmptyAreaMenu -> Column(modifier = modifier.fillMaxWidth()) {
            state.items.forEach { item ->
                WidgetMenuRow(item = item, onToggle = onToggle, onAction = onAction)
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
private fun WidgetMenuRow(item: WidgetMenuItem, onToggle: (WidgetMenuAction, Boolean) -> Unit, onAction: (WidgetMenuAction) -> Unit) {
    when (item) {
        is WidgetMenuItem.Toggle -> ToggleRow(item = item, onToggle = onToggle)
        is WidgetMenuItem.Action -> ActionRow(item = item, onAction = onAction)
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
    WidgetMenuAction.ClockStyle -> R.string.clock_style
    WidgetMenuAction.RemoveHeader -> R.string.remove_header
    WidgetMenuAction.RestoreHeader -> R.string.restore_header
    WidgetMenuAction.WidgetSettings -> R.string.widget_settings
}

private fun WidgetMenuAction.icon(): ImageVector = when (this) {
    WidgetMenuAction.Clock -> FontAwesomeIcons.Solid.Clock
    WidgetMenuAction.Date -> FontAwesomeIcons.Solid.Calendar
    WidgetMenuAction.ClockStyle -> FontAwesomeIcons.Solid.Font
    WidgetMenuAction.RemoveHeader -> FontAwesomeIcons.Solid.TrashAlt
    WidgetMenuAction.RestoreHeader -> FontAwesomeIcons.Solid.Th
    WidgetMenuAction.WidgetSettings -> FontAwesomeIcons.Solid.SlidersH
}
