package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.regular.Star
import compose.icons.fontawesomeicons.solid.ChevronRight
import compose.icons.fontawesomeicons.solid.Star
import core.data.apps.model.App
import core.data.apps.model.isPrivateApp
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.ui.composables.LaunchableIcon
import core.ui.composables.OutlinedText
import core.ui.layout.UNAVAILABLE_ALPHA
import core.ui.layout.minTouchTargetSize
import core.ui.util.performLongPressHaptic
import kotlin.math.roundToInt

private val favoriteStarSize: Dp = 28.dp
private val favoriteStarOutlineSize: Dp = 36.dp
private val launchableIconCenterOffset: Dp = 32.dp
private val folderSummaryChevronSize: Dp = 16.dp

@Immutable
data class LauncherItemActions(
    val onItemLaunch: (Launchable) -> Unit,
    val onItemLongPress: (Launchable) -> Unit,
    val onToggleFavorite: (Launchable) -> Unit,
    val onItemOpenPopup: (Launchable, PopupAnchor) -> Unit,
    val onItemAnchorChanged: (Launchable, PopupAnchor) -> Unit,
    val privateSpaceActions: PrivateSpaceActions,
    val railBounds: () -> Rect?
)

private class PopupAnchorHolder {
    var anchor: PopupAnchor? = null
}

private class LongPressGate {
    var isFired: Boolean = false
}

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
    isPopupSwipeEnabled: Boolean,
    itemActions: LauncherItemActions,
    summary: String? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val density: Float = LocalDensity.current.density
    val layoutDirection: LayoutDirection = LocalLayoutDirection.current
    val openPopupLabel: String = stringResource(R.string.open_popup)
    val currentItem: Launchable by rememberUpdatedState(item)
    val currentItemActions: LauncherItemActions by rememberUpdatedState(itemActions)
    val anchorHolder: PopupAnchorHolder = remember { PopupAnchorHolder() }
    val longPressGate: LongPressGate = remember { LongPressGate() }
    val canOpenPopup: Boolean = !isEditingFavorites &&
        !item.isPrivateApp &&
        (item is Folder || (isPopupSwipeEnabled && item is App))
    val canSwipePopup: Boolean = isPopupSwipeEnabled && canOpenPopup
    val isPaused: Boolean = item is App && item.isPaused
    val showsFavoriteToggle: Boolean = isEditingFavorites && !item.isPrivateApp
    val summaryDescription: String? = summary?.let { summaryText ->
        stringResource(R.string.folder_summary_description, item.name, summaryText)
    }
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (isPaused) UNAVAILABLE_ALPHA else 1f)
                .pointerInput(isEditingFavorites) {
                    if (!isEditingFavorites) {
                        detectTapGestures(
                            onPress = { longPressGate.isFired = false },
                            onTap = {
                                if (currentItem is Folder && !isEditingFavorites) {
                                    anchorHolder.anchor?.let { anchor -> currentItemActions.onItemOpenPopup(currentItem, anchor) }
                                } else {
                                    currentItemActions.onItemLaunch(currentItem)
                                }
                            },
                            onLongPress = {
                                longPressGate.isFired = true
                                currentItemActions.onItemLongPress(currentItem)
                                hapticFeedback.performLongPressHaptic()
                            }
                        )
                    }
                }
                .popupSwipe(
                    enabled = canSwipePopup,
                    railBounds = itemActions.railBounds,
                    isSwipeBlocked = { longPressGate.isFired },
                    onSwipeOpen = {
                        anchorHolder.anchor?.let { anchor -> currentItemActions.onItemOpenPopup(currentItem, anchor) }
                    }
                )
                .onGloballyPositioned { coordinates ->
                    val bounds: Rect = coordinates.boundsInRoot()
                    val iconCenterPx: Float = launchableIconCenterOffset.value * density
                    val iconCenterX: Float = if (layoutDirection == LayoutDirection.Rtl) {
                        bounds.right - iconCenterPx
                    } else {
                        bounds.left + iconCenterPx
                    }
                    val anchor: PopupAnchor = PopupAnchor(
                        rowTop = bounds.top.roundToInt(),
                        rowBottom = bounds.bottom.roundToInt(),
                        iconCenter = Offset(x = iconCenterX, y = bounds.top + iconCenterPx)
                    )
                    anchorHolder.anchor = anchor
                    currentItemActions.onItemAnchorChanged(currentItem, anchor)
                }
                .then(
                    if (summaryDescription != null && !isEditingFavorites) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = summaryDescription
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (canOpenPopup) {
                        Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(label = openPopupLabel) {
                                    val anchor: PopupAnchor? = anchorHolder.anchor
                                    if (anchor == null) {
                                        false
                                    } else {
                                        currentItemActions.onItemOpenPopup(currentItem, anchor)
                                        true
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (showsFavoriteToggle) {
                FavoriteToggleStar(
                    isFavorite = isFavorite,
                    onClick = { currentItemActions.onToggleFavorite(currentItem) }
                )
            }
            LaunchableIcon(launchable = item)
            Column(modifier = Modifier.weight(1f)) {
                OutlinedText(
                    text = item.name
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (summary != null) {
                Icon(
                    modifier = Modifier.size(folderSummaryChevronSize),
                    imageVector = FontAwesomeIcons.Solid.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
