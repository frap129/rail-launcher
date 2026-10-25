package core.popups

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.launchables.model.Launchable
import core.ui.composables.LaunchableGroupHeader
import core.ui.composables.SheetDragHandle

private val pickerIconSize = 32.dp
private val pickerIconGap = 12.dp
private val pickerRowHorizontalPadding = 20.dp
private val pickerRowVerticalPadding = 10.dp

/** The full-height sheet for choosing which Launchables a Pop-up shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchablePickerSheet(state: PopupPickerState.Picking, onConfirm: (selectedInOrder: List<String>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val checkedKeys: SnapshotStateList<String> = remember(state.selectedKeys) {
        mutableStateListOf<String>().apply { addAll(state.selectedKeys) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            PopupSheetTitleRow(
                title = stringResource(R.string.add_apps),
                actionLabel = stringResource(R.string.add),
                onAction = { onConfirm(checkedKeys.toList()) }
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                state.groups.forEachIndexed { groupIndex, group ->
                    item(key = "picker-group:$groupIndex") {
                        LaunchableGroupHeader(label = group.label, endPadding = pickerRowHorizontalPadding)
                    }
                    items(items = group.items, key = { item -> "picker-item:${item.key}" }) { item ->
                        PickerRow(
                            launchable = item,
                            isChecked = item.key in checkedKeys,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    checkedKeys.add(item.key)
                                } else {
                                    checkedKeys.remove(item.key)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(launchable: Launchable, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = pickerRowHorizontalPadding, vertical = pickerRowVerticalPadding)
    ) {
        AsyncImage(
            modifier = Modifier.size(pickerIconSize),
            model = launchable.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(pickerIconGap))
        Text(
            text = launchable.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(checked = isChecked, onCheckedChange = null)
    }
}
