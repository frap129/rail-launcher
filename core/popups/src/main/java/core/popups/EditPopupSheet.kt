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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Times
import core.data.folders.Folder
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.ui.R as CoreUiR
import core.ui.composables.DragHandle
import core.ui.composables.SheetDragHandle
import core.ui.util.performSegmentTickHaptic
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private val editShortcutIconSize = 32.dp
private val editItemIconSize = 32.dp
private val shortcutIconGap = 12.dp
private val sectionTitleStartPadding = 16.dp
private val sectionTitleTopPadding = 16.dp
private val sectionTitleBottomPadding = 8.dp
private val editItemRowVerticalPadding = 4.dp
private val editItemRemoveEndPadding = 4.dp
private val addAppsButtonHorizontalPadding = 16.dp
private val addAppsButtonVerticalPadding = 8.dp
private val addAppsIconGap = 8.dp
private const val UNAVAILABLE_ITEM_ALPHA: Float = 0.5f
private const val SOURCE_SEPARATOR: String = " · "

private fun popupItemKey(key: String): String = ITEM_KEY_PREFIX + key

/** The full-height sheet for editing what the owner's Pop-up shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPopupSheet(viewModel: PopupViewModel) {
    val editState: PopupEditState by viewModel.editState.collectAsState()
    val state: PopupEditState.Editing = editState as? PopupEditState.Editing ?: return
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val isFolder: Boolean = state.owner is Folder
    var displayedItems: List<PopupItem> by remember(state.owner.key) { mutableStateOf(state.items) }
    var draggedItemKey: String? by remember { mutableStateOf(null) }
    LaunchedEffect(state.items) {
        if (draggedItemKey == null) {
            displayedItems = state.items
        }
    }
    val reorderState: ReorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey: String? = popupItemDragKey(from.key)
        val toKey: String? = popupItemDragKey(to.key)
        if (fromKey == null || toKey == null) {
            return@rememberReorderableLazyListState
        }
        val fromIndex: Int = displayedItems.indexOfFirst { item -> item.launchable.key == fromKey }
        val toIndex: Int = displayedItems.indexOfFirst { item -> item.launchable.key == toKey }
        if (fromIndex < 0 || toIndex < 0) {
            return@rememberReorderableLazyListState
        }
        displayedItems = displayedItems.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        hapticFeedback.performSegmentTickHaptic()
    }
    ModalBottomSheet(
        onDismissRequest = viewModel::finishEdit,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            PopupSheetTitleRow(
                title = if (isFolder) {
                    stringResource(R.string.edit_folder_title, state.owner.name)
                } else {
                    stringResource(R.string.edit_popup_title, state.owner.name)
                },
                action = PopupSheetTitleAction(
                    label = stringResource(CoreUiR.string.done),
                    onClick = viewModel::finishEdit
                )
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (!isFolder) {
                    item(key = "edit_popup_shortcuts_title") {
                        SectionTitle(textResId = R.string.shortcuts)
                    }
                    when (val shortcuts: ShortcutsResult = state.shortcuts) {
                        is ShortcutsResult.Available -> if (shortcuts.shortcuts.isEmpty()) {
                            item(key = "edit_popup_shortcuts_empty") {
                                PopupEmptyState(messageResId = R.string.no_own_shortcuts)
                            }
                        } else {
                            items(items = shortcuts.shortcuts, key = { shortcut -> shortcut.key }) { shortcut ->
                                EditShortcutRow(
                                    shortcut = shortcut,
                                    isShown = shortcut.shortcutId !in state.hiddenIds,
                                    onShownChange = { isShown -> viewModel.setShortcutHidden(shortcut.shortcutId, !isShown) }
                                )
                            }
                        }

                        ShortcutsResult.Unavailable -> item(key = "edit_popup_shortcuts_unavailable") {
                            PopupEmptyState(messageResId = R.string.set_rail_as_home_for_shortcuts)
                        }

                        ShortcutsResult.ProfilePaused -> item(key = "edit_popup_shortcuts_paused") {
                            PopupEmptyState(messageResId = R.string.work_apps_paused)
                        }
                    }
                }
                item(key = "edit_popup_items_title") {
                    SectionTitle(textResId = R.string.items)
                }
                if (displayedItems.isEmpty()) {
                    item(key = "edit_popup_items_empty") {
                        PopupEmptyState(messageResId = R.string.add_items_hint)
                    }
                }
                items(items = displayedItems, key = { item -> popupItemKey(item.launchable.key) }) { item ->
                    ReorderableItem(state = reorderState, key = popupItemKey(item.launchable.key)) { _ ->
                        EditItemRow(
                            item = item,
                            sourceName = state.shortcutSourceNames[item.launchable.key],
                            dragHandle = {
                                DragHandle(
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            draggedItemKey = item.launchable.key
                                        },
                                        onDragStopped = {
                                            viewModel.setItemOrder(
                                                orderedItemKeys = displayedItems.map { candidate ->
                                                    candidate.launchable.key
                                                }
                                            )
                                            draggedItemKey = null
                                        }
                                    ),
                                    contentDescription = stringResource(R.string.reorder_item, item.launchable.name)
                                )
                            },
                            onRemove = { viewModel.removeItem(item.launchable) }
                        )
                    }
                }
                item(key = "edit_popup_add_apps") {
                    AddButton(labelResId = R.string.add_apps, onClick = viewModel::startPicking)
                }
                item(key = "edit_popup_add_shortcuts") {
                    AddButton(labelResId = R.string.add_shortcuts, onClick = viewModel::startPickingShortcuts)
                }
            }
        }
    }
}

private const val ITEM_KEY_PREFIX: String = "popup_item:"

private fun popupItemDragKey(key: Any?): String? =
    (key as? String)?.takeIf { value -> value.startsWith(ITEM_KEY_PREFIX) }?.removePrefix(ITEM_KEY_PREFIX)

@Composable
private fun SectionTitle(textResId: Int) {
    Text(
        text = stringResource(textResId),
        modifier = Modifier.padding(
            start = sectionTitleStartPadding,
            top = sectionTitleTopPadding,
            end = sectionTitleStartPadding,
            bottom = sectionTitleBottomPadding
        ),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
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
        Row(modifier = Modifier.weight(1f)) {
            PopupShortcutLabel(shortcut = shortcut)
        }
        Checkbox(checked = isShown, onCheckedChange = null)
    }
}

@Composable
private fun EditItemRow(item: PopupItem, sourceName: String?, dragHandle: @Composable () -> Unit, onRemove: () -> Unit) {
    val isUnavailable: Boolean = item is PopupItem.Shortcut && !item.isEnabled
    val label: String = if (sourceName != null) "${item.launchable.name}$SOURCE_SEPARATOR$sourceName" else item.launchable.name
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isUnavailable) UNAVAILABLE_ITEM_ALPHA else 1f)
            .padding(
                start = sectionTitleStartPadding,
                end = editItemRemoveEndPadding,
                top = editItemRowVerticalPadding,
                bottom = editItemRowVerticalPadding
            )
    ) {
        dragHandle()
        AsyncImage(
            modifier = Modifier.size(editItemIconSize),
            model = item.launchable.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(shortcutIconGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isUnavailable) {
                Text(
                    text = stringResource(R.string.unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Times,
                contentDescription = stringResource(R.string.remove_item, label)
            )
        }
    }
}

@Composable
private fun AddButton(labelResId: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = addAppsButtonHorizontalPadding, vertical = addAppsButtonVerticalPadding)
    ) {
        Icon(
            imageVector = FontAwesomeIcons.Solid.Plus,
            contentDescription = null,
            modifier = Modifier.size(popupIconSize)
        )
        Spacer(Modifier.width(addAppsIconGap))
        Text(stringResource(labelResId))
    }
}
