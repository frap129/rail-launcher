package feature.launcher

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.ui.composables.OutlinedText
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class AppListRowState(val isEditingFavorites: Boolean, val favoriteKeys: Set<String>)

// App List rows only read Favorites while Edit Favorites shows stars.
@Composable
internal fun rememberAppListRowState(isEditingFavorites: Boolean, favorites: StateFlow<List<Launchable>>): AppListRowState {
    if (!isEditingFavorites) {
        return AppListRowState(isEditingFavorites = false, favoriteKeys = emptySet())
    }
    val favoriteList: List<Launchable> by favorites.collectAsState()
    val favoriteKeys: Set<String> = rememberFavoriteKeySet(favoriteList)
    return AppListRowState(isEditingFavorites = true, favoriteKeys = favoriteKeys)
}

// A name starting with a Rail label (e.g. "⌂") forms an Initial group that
// shares the Builtin label, so the App List key includes the group kind.
internal fun LaunchableGroup.Listed.appListKey(): String = when (this) {
    is LaunchableGroup.Builtin -> "builtin:$label"
    is LaunchableGroup.InitialGroup -> "initial:$label"
}

@Composable
fun LauncherAppList(
    modifier: Modifier = Modifier,
    appList: List<LaunchableGroup.Listed>,
    lazyListState: LazyListState,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
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
                itemActions = itemActions
            )
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
    itemActions: LauncherItemActions
) {
    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
            OutlinedText(
                text = group.label,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 24.dp, 0.dp, 8.dp)
            )
        }
        group.items.forEach { item ->
            LauncherItem(
                item = item,
                isEditingFavorites = rowState.isEditingFavorites,
                isFavorite = item.key in rowState.favoriteKeys,
                itemActions = itemActions
            )
        }
    }
}
