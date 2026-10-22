package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetSpacing
import core.data.widgets.WidgetWidth
import core.widgets.WidgetModel

/** How Home lays out the Widget Area. */
sealed interface WidgetAreaLayout {
    data class Capped(val fraction: Float) : WidgetAreaLayout

    data object Uncapped : WidgetAreaLayout
}

internal fun WidgetAreaSettings.toLayout(): WidgetAreaLayout =
    if (heightLimitEnabled) WidgetAreaLayout.Capped(fraction = heightLimit.fraction) else WidgetAreaLayout.Uncapped

/** What the Widget Area shows once the first clock reading has arrived. */
data class WidgetAreaState(
    val places: List<WidgetPlaceModel> = emptyList(),
    val hasWidgets: Boolean = places.isNotEmpty(),
    val isEditing: Boolean = false,
    val stackIndicatorVisible: Boolean = true,
    val maxStackPages: Int = WidgetAreaSettings.DEFAULT_MAX_STACK_PAGES,
    val layout: WidgetAreaLayout = WidgetAreaLayout.Capped(fraction = WidgetAreaHeightLimit.DEFAULT.fraction),
    val topOffsetDp: Int = 0,
    val widgetSpacingDp: Int = WidgetSpacing.DEFAULT.dp,
    val mediaPlayerPlacement: MediaPlayerPlacement = MediaPlayerPlacement.BELOW_WIDGET_AREA,
    val animateOffset: Boolean = false,
    val listState: LazyListState = LazyListState()
)

@Immutable
data class WidgetPlaceModel(
    val id: Long,
    val heightDp: Int?,
    val currentPage: Int = 0,
    val widgets: List<WidgetModel>,
    val widthMode: WidgetWidth = WidgetWidth.Inset
) {
    val isStack: Boolean get() = widgets.size > 1
}
