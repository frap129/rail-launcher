package feature.launcher

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.launchables.model.appListKey
import core.ui.composables.LaunchableGroupHeader
import core.ui.composables.LaunchableSectionHeader
import kotlinx.coroutines.flow.Flow

/** The LazyColumn key of the Recently installed section, which is not one of the App List's groups. */
private const val RECENTLY_INSTALLED_ITEM_KEY: String = "recentlyInstalled"

@Immutable
data class AppListRowState(val isEditingFavorites: Boolean, val favoriteKeys: Set<String>, val isPopupSwipeEnabled: Boolean)

// App List rows only read Favorites while Edit Favorites shows stars.
@Composable
internal fun rememberAppListRowState(
    isEditingFavorites: Boolean,
    favorites: Flow<List<Launchable>>,
    isPopupSwipeEnabled: Boolean
): AppListRowState {
    val favoriteList: List<Launchable> by favorites.collectAsState(initial = emptyList())
    val favoriteKeys: Set<String> = if (isEditingFavorites) rememberFavoriteKeySet(favoriteList) else emptySet()
    return AppListRowState(
        isEditingFavorites = isEditingFavorites,
        favoriteKeys = favoriteKeys,
        isPopupSwipeEnabled = isPopupSwipeEnabled
    )
}

@Composable
fun LauncherAppList(
    modifier: Modifier = Modifier,
    appList: List<LaunchableGroup.Listed>,
    recentlyInstalled: RecentlyInstalledSection?,
    lazyListState: LazyListState,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    folderSummaries: Map<String, String>,
    topOffset: Dp,
    bottomOffset: Dp
) {
    val defaultBuffer: Dp = 100.dp
    var animated: Boolean by remember { mutableStateOf(false) }
    val topSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else topOffset,
        animationSpec = tween(durationMillis = 100, delayMillis = 275)
    )
    val bottomSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else bottomOffset,
        animationSpec = tween(durationMillis = 400, delayMillis = 275)
    )
    LazyColumn(
        modifier = modifier.testTag("launcherList"),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Spacer(Modifier.height(topSpacerHeightAnimator))
        }
        items(appList, key = { itemGroup -> itemGroup.appListKey() }) { itemGroup ->
            LauncherItemGroup(
                group = itemGroup,
                rowState = rowState,
                itemActions = itemActions,
                folderSummaries = folderSummaries
            )
        }
        if (recentlyInstalled != null) {
            item(key = RECENTLY_INSTALLED_ITEM_KEY) {
                LauncherRecentlyInstalledGroup(
                    section = recentlyInstalled,
                    rowState = rowState,
                    itemActions = itemActions,
                    folderSummaries = folderSummaries
                )
            }
        }
        item {
            Spacer(Modifier.height(bottomSpacerHeightAnimator))
        }
    }
    LaunchedEffect(Unit) {
        animated = true
    }
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
            LauncherAppListRow(item = item, rowState = rowState, itemActions = itemActions, folderSummaries = folderSummaries)
        }
        if (group is LaunchableGroup.PrivateSpace && !group.isLocked) {
            PrivateSpaceActionRows(actions = itemActions.privateSpaceActions)
        }
    }
}

/** The Recently installed section: its titled header and its Apps, after every group. */
@Composable
private fun LauncherRecentlyInstalledGroup(
    section: RecentlyInstalledSection,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    folderSummaries: Map<String, String>
) {
    Column {
        LaunchableSectionHeader(title = stringResource(R.string.recently_installed))
        section.apps.forEach { app ->
            LauncherAppListRow(item = app, rowState = rowState, itemActions = itemActions, folderSummaries = folderSummaries)
        }
    }
}

/** One App List row, shared by the groups and the Recently installed section. */
@Composable
private fun LauncherAppListRow(
    item: Launchable,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    folderSummaries: Map<String, String>
) {
    LauncherItem(
        item = item,
        isEditingFavorites = rowState.isEditingFavorites,
        isFavorite = item.key in rowState.favoriteKeys,
        isPopupSwipeEnabled = rowState.isPopupSwipeEnabled,
        itemActions = itemActions,
        secondaryText = folderSummaries[item.key]
    )
}
