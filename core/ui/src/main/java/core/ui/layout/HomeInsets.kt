package core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide

/** The 48dp touch target size, used as an exact size or as a minimum height. */
val minTouchTargetSize: Dp = 48.dp

/** The shared horizontal, top and bottom inset for Home content. */
val homeEdgePadding: Dp = 32.dp

/** The inset that keeps Home content clear of the Rail's touch column (64dp Rail plus the 16dp offset). */
val homeRailTouchInset: Dp = 80.dp

/** The start inset that keeps Home content clear of the Rail's touch column on [railSide]. */
fun homeRailStartInset(railSide: RailSide): Dp = if (railSide.isStart) homeRailTouchInset else homeEdgePadding

/** The end inset that keeps Home content clear of the Rail's touch column on [railSide]. */
fun homeRailEndInset(railSide: RailSide): Dp = if (railSide.isStart) homeEdgePadding else homeRailTouchInset

/** The start inset inside [homeEdgePadding] that keeps inset Widgets clear of the Rail's touch column. */
fun homeContentStartInset(railSide: RailSide): Dp = homeRailStartInset(railSide = railSide) - homeEdgePadding

/** The end inset inside [homeEdgePadding] that keeps inset Widgets clear of the Rail's touch column. */
fun homeContentEndInset(railSide: RailSide): Dp = homeRailEndInset(railSide = railSide) - homeEdgePadding

/** The padding that keeps full-width Home content clear of the Rail's touch column on [railSide]. */
fun homeRailContentPaddingValues(railSide: RailSide, bottom: Dp = 0.dp): PaddingValues = PaddingValues(
    start = homeRailStartInset(railSide = railSide),
    end = homeEdgePadding,
    bottom = bottom
)

/** The padding that keeps Home content clear of the Rail's touch column on [railSide]. */
fun homeContentPaddingValues(railSide: RailSide): PaddingValues = PaddingValues(
    start = homeContentStartInset(railSide = railSide),
    end = homeContentEndInset(railSide = railSide)
)

/** The padding that keeps Favorites content clear of a Start Rail, leaving an End Rail at [homeEdgePadding]. */
fun favoritesContentPaddingValues(railSide: RailSide): PaddingValues = PaddingValues(start = homeContentStartInset(railSide = railSide))

/** Pads Favorites content clear of a Start Rail, leaving an End Rail at [homeEdgePadding]. */
fun Modifier.favoritesContentPadding(railSide: RailSide): Modifier = padding(favoritesContentPaddingValues(railSide = railSide))

/** The content width an inset Widget occupies on a screen [screenWidth] wide. */
fun insetWidgetContentWidth(screenWidth: Dp, railSide: RailSide): Dp =
    screenWidth - homeRailStartInset(railSide = railSide) - homeRailEndInset(railSide = railSide)
