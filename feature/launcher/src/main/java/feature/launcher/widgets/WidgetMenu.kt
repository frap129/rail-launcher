package feature.launcher.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import core.data.prefs.ClockStyle
import core.ui.R as CoreUiR
import core.ui.composables.RowLeadingIcon
import core.ui.composables.SettingsSwitchRow
import core.ui.composables.SheetActionRow
import core.ui.composables.SheetRow
import core.ui.composables.SheetTextTitle
import core.ui.composables.sheetContainerColor
import core.ui.widgets.SheetClockStyleChooser
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
    val header: @Composable () -> Unit
    val items: List<WidgetMenuItem>
    when (state) {
        WidgetMenuState.Closed -> return

        is WidgetMenuState.ClockStyleChooser -> {
            SheetClockStyleChooser(
                selected = state.selected,
                previews = state.previews,
                onSelect = onClockStyleSelected,
                modifier = modifier
            )
            return
        }

        is WidgetMenuState.HeaderMenu -> {
            header = { SheetTextTitle(title = stringResource(R.string.header_title)) }
            items = state.items
        }

        is WidgetMenuState.EmptyAreaMenu -> {
            header = {}
            items = state.items
        }

        is WidgetMenuState.AppWidgetMenu -> {
            header = { WidgetMenuTitle(icon = state.icon, label = state.label) }
            items = state.items
        }

        is WidgetMenuState.MediaPlayerMenu -> {
            header = { WidgetMenuTitle(icon = state.icon, label = state.label) }
            items = state.items
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        header()
        items.forEach { item ->
            WidgetMenuRow(
                item = item,
                onToggle = onToggle,
                onAction = onAction,
                onChoiceClick = onChoiceClick,
                onChoiceSelected = onChoiceSelected
            )
        }
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
    SettingsSwitchRow(
        title = stringResource(item.id.label()),
        checked = item.checked,
        onCheckedChange = { checked -> onToggle(item.id, checked) },
        enabled = item.enabled,
        supportingText = item.supportingText?.let { supportingText -> stringResource(supportingText) },
        leadingIcon = item.id.icon(),
        containerColor = sheetContainerColor,
        onClick = if (item.rowOpensAction) ({ onAction(item.id) }) else null
    )
}

@Composable
private fun ActionRow(item: WidgetMenuItem.Action, onAction: (WidgetMenuAction) -> Unit) {
    SheetActionRow(
        imageVector = item.id.icon(),
        label = item.labelArg?.let { arg -> stringResource(item.id.label(), arg) } ?: stringResource(item.id.label()),
        onClick = { onAction(item.id) }
    )
}

@Composable
private fun ChoiceRow(
    item: WidgetMenuItem.Choice,
    onChoiceClick: (WidgetMenuAction) -> Unit,
    onChoiceSelected: (WidgetMenuAction, WidgetMenuChoiceKey) -> Unit
) {
    Column {
        SheetRow(
            modifier = Modifier.clickable(role = Role.Button) { onChoiceClick(item.id) },
            headlineContent = { Text(stringResource(item.id.label())) },
            supportingContent = { Text(stringResource(item.valueLabel)) },
            leadingContent = { RowLeadingIcon(imageVector = item.id.icon()) }
        )
        if (item.expanded) {
            item.options.forEach { option ->
                val isSelected: Boolean = option.key == item.selectedKey
                SheetRow(
                    modifier = Modifier.selectable(
                        selected = isSelected,
                        enabled = option.enabled,
                        role = Role.RadioButton,
                        onClick = { onChoiceSelected(item.id, option.key) }
                    ),
                    headlineContent = { Text(stringResource(option.label)) },
                    leadingContent = { RadioButton(selected = isSelected, onClick = null, enabled = option.enabled) }
                )
            }
        }
    }
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
    WidgetMenuAction.Resize -> CoreUiR.string.resize_widget
    WidgetMenuAction.Reconfigure -> CoreUiR.string.reconfigure_widget
    WidgetMenuAction.RemoveHeader -> R.string.remove_header
    WidgetMenuAction.RemoveFromStack -> CoreUiR.string.remove_from_stack
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
