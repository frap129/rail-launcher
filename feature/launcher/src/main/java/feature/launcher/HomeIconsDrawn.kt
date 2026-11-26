package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import core.ui.HomeIconsDrawnReport
import core.ui.model.data.isFavoriteKey

/**
 * Reports Home icons drawn once Home can be drawn and has settled the Favorite icons it shows,
 * so the startup benchmark can time them apart from Home fully drawn. A Home with no Favorite
 * reports at once; one with Favorites reports after the frame that draws them.
 */
@Composable
internal fun HomeIconsDrawnEffect(home: LauncherUiState.Home, isHomeDrawable: Boolean, report: HomeIconsDrawnReport) {
    val hasFavorites: Boolean = home.favorites.isNotEmpty()
    var hasLaidOutFavorites: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(isHomeDrawable, hasFavorites) {
        hasLaidOutFavorites = false
        if (isHomeDrawable && hasFavorites) {
            // Home's list lays its Favorites out in the first frame it can draw them.
            withFrameNanos { }
            hasLaidOutFavorites = true
        }
    }
    // Derived so Home's scrolls and layout changes recompose only when the visible Favorites change.
    val visibleFavoriteKeys: Set<String> by remember(home.lazyListState) {
        derivedStateOf { visibleFavoriteKeys(lazyListState = home.lazyListState) }
    }
    val isHomeIconsDrawn: Boolean = when {
        !isHomeDrawable || report.isReported -> false
        !hasFavorites -> true
        !hasLaidOutFavorites -> false
        else -> report.settledIcons.hasSettledEvery(visibleFavoriteKeys = visibleFavoriteKeys)
    }
    LaunchedEffect(isHomeIconsDrawn, hasFavorites) {
        if (isHomeIconsDrawn) {
            if (hasFavorites) {
                // The report follows the frame that draws the icons, so it never precedes them.
                withFrameNanos { }
            }
            report.report()
        }
    }
}

private fun visibleFavoriteKeys(lazyListState: LazyListState): Set<String> = lazyListState.layoutInfo.visibleItemsInfo
    .map { info -> info.key }
    .filterIsInstance<String>()
    .filter(::isFavoriteKey)
    .toSet()
