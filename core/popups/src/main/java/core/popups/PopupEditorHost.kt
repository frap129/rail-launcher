package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Pop-up editor and the item and Shortcut pickers it opens; renders nothing while they are closed. */
@Composable
fun PopupEditorHost(viewModel: PopupViewModel) {
    val pickerState: PopupPickerState by viewModel.pickerState.collectAsState()
    val shortcutPickerState: ShortcutPickerState by viewModel.shortcutPickerState.collectAsState()
    EditPopupSheet(viewModel = viewModel)
    if (pickerState is PopupPickerState.Picking) {
        LaunchablePickerSheet(
            state = pickerState as PopupPickerState.Picking,
            onConfirm = viewModel::confirmPicking,
            onDismiss = viewModel::cancelPicking
        )
    }
    if (shortcutPickerState is ShortcutPickerState.Picking) {
        ShortcutPickerSheet(
            state = shortcutPickerState as ShortcutPickerState.Picking,
            onConfirm = viewModel::confirmShortcuts,
            onDismiss = viewModel::cancelShortcutPicking
        )
    }
}
