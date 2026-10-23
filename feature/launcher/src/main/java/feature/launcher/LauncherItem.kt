package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.regular.Star
import compose.icons.fontawesomeicons.solid.Star
import core.data.launchables.model.Launchable
import core.ui.composables.OutlinedText
import core.ui.layout.minTouchTargetSize
import core.ui.util.performLongPressHaptic

private val favoriteStarSize: Dp = 28.dp
private val favoriteStarOutlineSize: Dp = 36.dp

@Immutable
data class LauncherItemActions(
    val onItemLaunch: (Launchable) -> Unit,
    val onItemLongPress: (Launchable) -> Unit,
    val onToggleFavorite: (Launchable) -> Unit
)

private fun List<Launchable>.favoriteKeys(): List<String> = map { launchable -> launchable.key }

@Composable
internal fun rememberFavoriteKeys(favorites: List<Launchable>): List<String> = remember(favorites) { favorites.favoriteKeys() }

@Composable
internal fun rememberFavoriteKeySet(favorites: List<Launchable>): Set<String> = remember(favorites) { favorites.favoriteKeys().toHashSet() }

@Composable
fun LauncherItem(
    item: Launchable,
    isEditingFavorites: Boolean,
    isFavorite: Boolean,
    itemActions: LauncherItemActions,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val currentItem: Launchable by rememberUpdatedState(item)
    val currentItemActions: LauncherItemActions by rememberUpdatedState(itemActions)
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(isEditingFavorites) {
                    if (!isEditingFavorites) {
                        detectTapGestures(
                            onTap = { currentItemActions.onItemLaunch(currentItem) },
                            onLongPress = {
                                currentItemActions.onItemLongPress(currentItem)
                                hapticFeedback.performLongPressHaptic()
                            }
                        )
                    }
                }
        ) {
            if (isEditingFavorites) {
                FavoriteToggleStar(
                    isFavorite = isFavorite,
                    onClick = { currentItemActions.onToggleFavorite(currentItem) }
                )
            }
            LaunchableIcon(launchable = item)
            OutlinedText(
                text = item.name
            )
        }
    }
}

@Composable
internal fun LaunchableIcon(launchable: Launchable) {
    AsyncImage(
        modifier = Modifier.size(64.dp).padding(10.dp),
        model = launchable.icon,
        contentDescription = launchable.name
    )
}

@Composable
private fun FavoriteToggleStar(isFavorite: Boolean, onClick: () -> Unit) {
    val label: String = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)
    val imageVector: ImageVector = if (isFavorite) FontAwesomeIcons.Solid.Star else FontAwesomeIcons.Regular.Star
    Box(
        modifier = Modifier
            .size(minTouchTargetSize)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.size(favoriteStarOutlineSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentDescription = null
        )
        Image(
            modifier = Modifier.size(favoriteStarSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            contentDescription = null
        )
    }
}
