package core.popups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronDown
import compose.icons.fontawesomeicons.solid.ChevronUp
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.ui.composables.LaunchableGroupHeader
import core.ui.composables.SheetDragHandle

private val pickerIconSize = 32.dp
private val pickerIconGap = 12.dp
private val pickerRowHorizontalPadding = 20.dp
private val pickerRowVerticalPadding = 10.dp
private val pickerShortcutRowStartPadding = pickerRowHorizontalPadding + pickerIconSize + pickerIconGap
private const val PAUSED_APP_ALPHA: Float = 0.5f

/** The full-height sheet for choosing which Shortcuts a Pop-up shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutPickerSheet(state: ShortcutPickerState.Picking, onConfirm: (selectedInOrder: List<String>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val checkedKeys: SnapshotStateList<String> = remember(state.selectedKeys) {
        mutableStateListOf<String>().apply { addAll(state.selectedKeys) }
    }
    val expandedApps: SnapshotStateList<String> = remember { mutableStateListOf() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            PopupSheetTitleRow(
                title = stringResource(R.string.add_shortcuts),
                action = PopupSheetTitleAction(
                    label = stringResource(R.string.add),
                    onClick = { onConfirm(checkedKeys.toList()) }
                )
            )
            if (state.shortcutsUnavailable) {
                PopupEmptyState(messageResId = R.string.set_rail_as_home_to_add_shortcuts)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    state.groups.forEachIndexed { groupIndex, group ->
                        item(key = "shortcut-picker-group:$groupIndex") {
                            LaunchableGroupHeader(label = group.label, endPadding = pickerRowHorizontalPadding)
                        }
                        items(items = group.apps, key = { appShortcuts -> "shortcut-picker-app:${appShortcuts.app.key}" }) { appShortcuts ->
                            PickerAppSection(
                                appShortcuts = appShortcuts,
                                checkedKeys = checkedKeys,
                                isExpanded = appShortcuts.app.key in expandedApps,
                                onToggleExpanded = {
                                    if (appShortcuts.app.key in expandedApps) {
                                        expandedApps.remove(appShortcuts.app.key)
                                    } else {
                                        expandedApps.add(appShortcuts.app.key)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerAppSection(
    appShortcuts: AppShortcuts,
    checkedKeys: SnapshotStateList<String>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val result: ShortcutsResult = appShortcuts.result
    val isPaused: Boolean = result is ShortcutsResult.ProfilePaused
    val canExpand: Boolean = result is ShortcutsResult.Available && result.shortcuts.isNotEmpty()
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isPaused) PAUSED_APP_ALPHA else 1f)
                .clickable(enabled = canExpand, onClick = onToggleExpanded)
                .padding(horizontal = pickerRowHorizontalPadding, vertical = pickerRowVerticalPadding)
        ) {
            AsyncImage(
                modifier = Modifier.size(pickerIconSize),
                model = appShortcuts.app.icon,
                contentDescription = null
            )
            Spacer(Modifier.width(pickerIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appShortcuts.app.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isPaused) {
                    Text(
                        text = stringResource(R.string.work_apps_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canExpand) {
                Icon(
                    imageVector = if (isExpanded) FontAwesomeIcons.Solid.ChevronUp else FontAwesomeIcons.Solid.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(popupIconSize)
                )
            }
        }
        if (isExpanded && result is ShortcutsResult.Available) {
            result.shortcuts.forEach { shortcut ->
                PickerShortcutRow(
                    shortcut = shortcut,
                    isChecked = shortcut.key in checkedKeys,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            checkedKeys.add(shortcut.key)
                        } else {
                            checkedKeys.remove(shortcut.key)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerShortcutRow(shortcut: ShortcutLaunchable, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(
                start = pickerShortcutRowStartPadding,
                end = pickerRowHorizontalPadding,
                top = pickerRowVerticalPadding,
                bottom = pickerRowVerticalPadding
            )
    ) {
        AsyncImage(
            modifier = Modifier.size(popupIconSize),
            model = shortcut.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(pickerIconGap))
        PopupShortcutLabel(shortcut = shortcut, modifier = Modifier.weight(1f))
        Checkbox(checked = isChecked, onCheckedChange = null)
    }
}
