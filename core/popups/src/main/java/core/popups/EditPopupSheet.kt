package core.popups

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle

private val editShortcutIconSize = 32.dp
private val shortcutIconGap = 12.dp
private val titleHorizontalPadding = 20.dp
private val titleVerticalPadding = 10.dp
private val sectionTitleStartPadding = 16.dp
private val sectionTitleTopPadding = 16.dp
private val sectionTitleBottomPadding = 8.dp

/** The full-height sheet for editing what the owner's Pop-up shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPopupSheet(viewModel: PopupViewModel) {
    val editState: PopupEditState by viewModel.editState.collectAsState()
    val state: PopupEditState.Editing = editState as? PopupEditState.Editing ?: return
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::finishEdit,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            EditPopupTitle(owner = state.owner, onDone = viewModel::finishEdit)
            Text(
                text = stringResource(R.string.shortcuts),
                modifier = Modifier.padding(
                    start = sectionTitleStartPadding,
                    top = sectionTitleTopPadding,
                    end = sectionTitleStartPadding,
                    bottom = sectionTitleBottomPadding
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
            when (val shortcuts: ShortcutsResult = state.shortcuts) {
                is ShortcutsResult.Available -> if (shortcuts.shortcuts.isEmpty()) {
                    PopupEmptyState(messageResId = R.string.no_own_shortcuts)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items = shortcuts.shortcuts, key = { shortcut -> shortcut.key }) { shortcut ->
                            EditShortcutRow(
                                shortcut = shortcut,
                                isShown = shortcut.shortcutId !in state.hiddenIds,
                                onShownChange = { isShown -> viewModel.setShortcutHidden(shortcut.shortcutId, !isShown) }
                            )
                        }
                    }
                }

                ShortcutsResult.Unavailable -> PopupEmptyState(messageResId = R.string.set_rail_as_home_for_shortcuts)

                ShortcutsResult.ProfilePaused -> PopupEmptyState(messageResId = R.string.work_apps_paused)
            }
        }
    }
}

@Composable
private fun EditPopupTitle(owner: Launchable, onDone: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titleHorizontalPadding, vertical = titleVerticalPadding)
    ) {
        Text(
            text = stringResource(R.string.edit_popup_title, owner.name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall
        )
        TextButton(onClick = onDone) {
            Text(stringResource(CoreUiR.string.done))
        }
    }
}

@Composable
private fun EditShortcutRow(shortcut: ShortcutLaunchable, isShown: Boolean, onShownChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isShown,
                role = Role.Checkbox,
                onValueChange = onShownChange
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        AsyncImage(
            modifier = Modifier.size(editShortcutIconSize),
            model = shortcut.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(shortcutIconGap))
        Box(modifier = Modifier.weight(1f)) {
            PopupShortcutLabel(shortcut = shortcut)
        }
        Checkbox(checked = isShown, onCheckedChange = null)
    }
}
