package core.ui.widgets

import core.data.icons.model.Icon
import core.data.testing.TEST_APP_WIDGET_ID
import core.data.testing.WIDGET_LABEL
import core.data.testing.WIDGET_PROVIDER_PACKAGE
import core.data.widgets.WidgetAvailability

/** An App Widget model with the defaults shared by Widget Area and Pop-up tests. */
fun createAppWidgetModel(
    id: Long,
    appWidgetId: Int = TEST_APP_WIDGET_ID,
    provider: String = WIDGET_PROVIDER_PACKAGE,
    availability: WidgetAvailability = WidgetAvailability.Available
): AppWidgetModel = AppWidgetModel(
    id = id,
    appWidgetId = appWidgetId,
    provider = provider,
    label = WIDGET_LABEL,
    icon = Icon.RailIcon(resId = 0),
    minHeightDp = 0,
    isWorkProfile = false,
    availability = availability
)

/** The Widget Area callbacks a test composes without any behavior. */
fun widgetAreaActions(): WidgetAreaActions = WidgetAreaActions(
    menuEnabled = true,
    onHomeHeight = {},
    onWidgetAreaWidth = {},
    onTapTarget = {},
    onHeaderLongPress = {},
    onHeaderHeight = {},
    onAppWidgetLongPress = {},
    onAppWidgetUnpause = {},
    onPageSettled = { _, _ -> },
    onEmptyAreaLongPress = {},
    onFinishResize = {},
    onDragResize = {},
    onResetResize = {},
    onMovePlace = { _, _ -> },
    onRemovePlace = {},
    onToggleExpand = {},
    onMovePage = { _, _, _ -> },
    onRemovePage = { _, _ -> },
    onSplitPage = { _, _, _ -> },
    onMergePlace = { _, _ -> },
    onMediaPlayerOpen = {},
    onMediaPlayerLongPress = {},
    onMediaCommand = {},
    onScrolledToPlace = {}
)
