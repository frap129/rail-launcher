package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.ui.R
import core.ui.layout.favoritesContentPadding
import core.ui.popups.PopupRow

private const val SUGGESTION_ALPHA: Float = 0.7f

/** A Home suggestion: a Favorite-like row drawn quieter and announced as suggested. */
@Composable
fun HomeSuggestionRow(
    suggestion: Launchable,
    railSide: RailSide,
    itemActions: LauncherItemActions,
    isPopupSwipeEnabled: Boolean,
    isRowFocusEnabled: Boolean = true
) {
    HomeItem(
        item = suggestion,
        isFavorite = false,
        isEditingFavorites = false,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = isPopupSwipeEnabled,
        itemActions = itemActions,
        accessibilityAnnouncement = stringResource(R.string.suggested),
        menuSuggestion = MenuSuggestion.Home,
        modifier = Modifier
            .alpha(SUGGESTION_ALPHA)
            .favoritesContentPadding(railSide = railSide)
    )
}

/** A [LauncherItem] on Home, whose Pop-up opens from the Home row. */
@Composable
fun HomeItem(
    item: Launchable,
    isFavorite: Boolean,
    isEditingFavorites: Boolean,
    isRowFocusEnabled: Boolean,
    isPopupSwipeEnabled: Boolean,
    itemActions: LauncherItemActions,
    menuSuggestion: MenuSuggestion,
    modifier: Modifier,
    secondaryText: String? = null,
    accessibilityAnnouncement: String? = null,
    onIconSettled: (() -> Unit)? = null
) {
    LauncherItem(
        item = item,
        isEditingFavorites = isEditingFavorites,
        isFavorite = isFavorite,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = isPopupSwipeEnabled,
        popupRow = PopupRow.Home,
        itemActions = itemActions,
        secondaryText = secondaryText,
        accessibilityAnnouncement = accessibilityAnnouncement,
        menuSuggestion = menuSuggestion,
        onIconSettled = onIconSettled,
        modifier = modifier
    )
}
