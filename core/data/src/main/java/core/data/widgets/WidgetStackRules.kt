package core.data.widgets

/** Whether a stack of [pageCount] pages can take [additionalPages] more within [maxStackPages]. */
fun canGrowStack(pageCount: Int, maxStackPages: Int, additionalPages: Int = 1): Boolean = pageCount + additionalPages <= maxStackPages

/** Whether the place can take [additionalPages] more Stack pages within [maxStackPages]. */
fun WidgetPlace.canGrowStack(maxStackPages: Int, additionalPages: Int = 1): Boolean =
    canGrowStack(pageCount = widgets.size, maxStackPages = maxStackPages, additionalPages = additionalPages)

/** The bounds a place is resized against: the intersection of its pages for a stack, its lone page's otherwise. */
fun List<PlacedWidget>.stackResizeBounds(
    headerHeightDp: Int? = null,
    providerInfo: (PlacedWidget.AppWidget) -> WidgetProviderInfo?
): ResizeBounds? {
    val pageBounds: List<ResizeBounds> = map { widget -> widget.resizeBounds(headerHeightDp = headerHeightDp, providerInfo = providerInfo) }
    return if (size > 1) ResizeBounds.stackBounds(pageBounds) else pageBounds.firstOrNull()
}

/** Whether Resize is offered: every page must be available and [bounds] resizable. */
fun List<PlacedWidget>.canResizeStack(bounds: ResizeBounds?): Boolean {
    val pages: List<PlacedWidget.AppWidget> = filterIsInstance<PlacedWidget.AppWidget>()
    return pages.isNotEmpty() &&
        pages.all { widget -> widget.availability == WidgetAvailability.Available } &&
        bounds?.isResizable == true
}

/** The tallest page default height for a stack, the lone page's default otherwise; null when no page has one. */
fun List<PlacedWidget>.stackDefaultHeightDp(providerInfo: (PlacedWidget.AppWidget) -> WidgetProviderInfo?): Int? {
    val defaultsDp: List<Int> = mapNotNull { widget ->
        (widget as? PlacedWidget.AppWidget)?.let { appWidget -> providerInfo(appWidget)?.minHeightDp ?: appWidget.defaultHeightDp }
    }
    return if (size > 1) defaultsDp.maxOrNull() else defaultsDp.firstOrNull()
}

/** Whether the place shows the Widget Stack indicator, given the user's setting. */
fun List<PlacedWidget>.stackIndicatorVisible(settingVisible: Boolean): Boolean = size > 1 && settingVisible
