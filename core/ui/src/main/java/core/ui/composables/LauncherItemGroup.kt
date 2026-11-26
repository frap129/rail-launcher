package core.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.ui.popups.PopupRow
import kotlinx.coroutines.flow.Flow

@Immutable
data class AppListRowState(
    val isEditingFavorites: Boolean,
    val favoriteKeys: Set<String>,
    val isPopupSwipeEnabled: Boolean,
    val isRowFocusEnabled: Boolean = true
)

// App List rows only read Favorites while Edit Favorites shows stars.
@Composable
fun rememberAppListRowState(
    isEditingFavorites: Boolean,
    favorites: Flow<List<Launchable>>,
    isRowFocusEnabled: Boolean,
    isPopupSwipeEnabled: Boolean
): AppListRowState {
    val favoriteList: List<Launchable> by favorites.collectAsState(initial = emptyList())
    val favoriteKeys: Set<String> = if (isEditingFavorites) rememberFavoriteKeySet(favoriteList) else emptySet()
    return AppListRowState(
        isEditingFavorites = isEditingFavorites,
        favoriteKeys = favoriteKeys,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = isPopupSwipeEnabled
    )
}

@Composable
fun LauncherItemGroup(
    modifier: Modifier = Modifier,
    group: LaunchableGroup.Listed,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    folderSummaries: Map<String, String> = emptyMap()
) {
    Column(modifier = modifier) {
        LaunchableGroupHeader(group = group)
        if (group is LaunchableGroup.PrivateSpace) {
            PrivateSpaceRow(
                isLocked = group.isLocked,
                onUnlock = itemActions.privateSpaceActions.onUnlock
            )
        }
        group.items.forEach { item ->
            LauncherAppListRow(
                item = item,
                popupRow = PopupRow.Group,
                rowState = rowState,
                itemActions = itemActions,
                folderSummaries = folderSummaries
            )
        }
        if (group is LaunchableGroup.PrivateSpace && !group.isLocked) {
            PrivateSpaceActionRows(actions = itemActions.privateSpaceActions)
        }
    }
}

/** One App List row, shared by the groups and the Recently installed section. */
@Composable
fun LauncherAppListRow(
    item: Launchable,
    popupRow: PopupRow,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    folderSummaries: Map<String, String>
) {
    LauncherItem(
        item = item,
        isEditingFavorites = rowState.isEditingFavorites,
        isFavorite = item.key in rowState.favoriteKeys,
        isRowFocusEnabled = rowState.isRowFocusEnabled,
        isPopupSwipeEnabled = rowState.isPopupSwipeEnabled,
        popupRow = popupRow,
        itemActions = itemActions,
        secondaryText = folderSummaries[item.key]
    )
}
