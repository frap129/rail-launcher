package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Pop-up editor and the item and Shortcut pickers it opens; renders nothing while they are closed. */
@Composable
fun PopupEditorHost(editorViewModel: PopupEditorViewModel, pickerViewModel: PopupPickerViewModel) {
    val pickerState: PopupPickerState by pickerViewModel.pickerState.collectAsState()
    val shortcutPickerState: ShortcutPickerState by pickerViewModel.shortcutPickerState.collectAsState()
    EditPopupSheet(editorViewModel = editorViewModel, pickerViewModel = pickerViewModel)
    if (pickerState is PopupPickerState.Picking) {
        LaunchablePickerSheet(
            state = pickerState as PopupPickerState.Picking,
            onConfirm = pickerViewModel::confirmPicking,
            onDismiss = pickerViewModel::cancelPicking
        )
    }
    if (shortcutPickerState is ShortcutPickerState.Picking) {
        ShortcutPickerSheet(
            state = shortcutPickerState as ShortcutPickerState.Picking,
            onConfirm = pickerViewModel::confirmShortcuts,
            onDismiss = pickerViewModel::cancelShortcutPicking
        )
    }
}
