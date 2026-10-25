package core.popups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import core.ui.composables.LaunchableGroupHeader
import core.ui.composables.SheetDragHandle

private const val PICKER_ITEM_KEY_PREFIX: String = "picker-item:"
private const val PICKER_GROUP_KEY_PREFIX: String = "picker-group:"

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
                action = PopupSheetTitleAction(
                    label = stringResource(R.string.add),
                    onClick = { onConfirm(checkedKeys.toList()) }
                )
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                state.groups.forEachIndexed { groupIndex, group ->
                    item(key = "$PICKER_GROUP_KEY_PREFIX$groupIndex") {
                        LaunchableGroupHeader(label = group.label, endPadding = checkboxRowHorizontalPadding)
                    }
                    items(items = group.items, key = { item -> "$PICKER_ITEM_KEY_PREFIX${item.key}" }) { item ->
                        CheckboxLaunchableRow(
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
