package feature.launcher.widgets

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
