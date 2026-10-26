package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Pop-up editor and the item and Shortcut pickers it opens; renders nothing while they are closed. */
@Composable
fun PopupEditorHost(editor: IPopupEditor, picker: IPopupPicker) {
    val editState: PopupEditState by editor.editState.collectAsState()
    val pickerState: PopupPickerState by picker.pickerState.collectAsState()
    val shortcutPickerState: ShortcutPickerState by picker.shortcutPickerState.collectAsState()
    val editing: PopupEditState.Editing? = editState as? PopupEditState.Editing
    if (editing != null) {
        EditPopupSheet(
            state = editing,
            onFinishEdit = editor::finishEdit,
            onShortcutHiddenChange = editor::setShortcutHidden,
            onItemOrderChange = editor::setItemOrder,
            onRemoveItem = editor::removeEditedItem,
            onAddApps = { picker.startPicking(editing.owner) },
            onAddShortcuts = { picker.startPickingShortcuts(editing.owner) }
        )
    }
    if (pickerState is PopupPickerState.Picking) {
        LaunchablePickerSheet(
            state = pickerState as PopupPickerState.Picking,
            onConfirm = picker::confirmPicking,
            onDismiss = picker::cancelPicking
        )
    }
    if (shortcutPickerState is ShortcutPickerState.Picking) {
        ShortcutPickerSheet(
            state = shortcutPickerState as ShortcutPickerState.Picking,
            onConfirm = picker::confirmShortcuts,
            onDismiss = picker::cancelShortcutPicking
        )
    }
}
