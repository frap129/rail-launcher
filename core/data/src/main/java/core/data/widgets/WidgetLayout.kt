package core.data.widgets

import core.data.room.dao.WidgetAreaDao

/** Whether a Widget Stack of [pageCount] pages fits within [maxStackPages]. */
fun isWithinStackCap(pageCount: Int, maxStackPages: Int): Boolean = pageCount <= maxStackPages

/** Whether a Widget Stack of [pageCount] pages can take [additionalPages] more within [maxStackPages]. */
fun canGrowStack(pageCount: Int, maxStackPages: Int, additionalPages: Int = 1): Boolean =
    isWithinStackCap(pageCount = pageCount + additionalPages, maxStackPages = maxStackPages)

/** Thrown when a write would grow a Widget Stack past the Stack cap. */
class WidgetStackFullException(message: String) : IllegalStateException(message) {
    companion object {
        /** The rejection for a Stack capped at [maxStackPages] pages. */
        fun forMaxPages(maxStackPages: Int): WidgetStackFullException =
            WidgetStackFullException(message = "Widget Stack cannot hold more than $maxStackPages pages")
    }
}

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

/** The page height a place falls back to: its tallest page default or the bounds minimum. */
fun List<PlacedWidget>.resolvedDefaultHeightDp(bounds: ResizeBounds, providerInfo: (PlacedWidget.AppWidget) -> WidgetProviderInfo?): Int =
    stackDefaultHeightDp(providerInfo = providerInfo) ?: bounds.minDp

/** Whether the place shows the Widget Stack indicator, given the user's setting. */
fun List<PlacedWidget>.stackIndicatorVisible(settingVisible: Boolean): Boolean = size > 1 && settingVisible

/** The provider info for a placed App Widget, read from [IAppWidgetHostSource]. */
fun IAppWidgetHostSource.providerInfoFor(widget: PlacedWidget.AppWidget): WidgetProviderInfo? =
    providerInfo(id = AppWidgetId(widget.appWidgetId))

/** The page and height a place keeps after its pages change. */
data class WidgetPlaceLayout(val currentPage: Int, val heightDp: Int?)

/** [places] with [widgetId] appended as a page of [placeId], or null when that would exceed [maxStackPages]. */
fun addDraftPage(places: List<PlaceDraft>, placeId: Long, widgetId: Long, maxStackPages: Int): List<PlaceDraft>? {
    val place: PlaceDraft = places.firstOrNull { draft -> draft.id == placeId } ?: return null
    if (!canGrowStack(pageCount = place.widgetIds.size, maxStackPages = maxStackPages)) {
        return null
    }
    return places.map { draft ->
        if (draft.id == placeId) draft.copy(widgetIds = draft.widgetIds + widgetId) else draft
    }
}

/** [places] with the single-page [sourcePlaceId] merged into [targetPlaceId], or null when either place or the source is invalid. */
fun mergeDraftPlaces(places: List<PlaceDraft>, sourcePlaceId: Long, targetPlaceId: Long): List<PlaceDraft>? {
    if (sourcePlaceId == targetPlaceId) {
        return null
    }
    val source: PlaceDraft = places.firstOrNull { draft -> draft.id == sourcePlaceId } ?: return null
    val target: PlaceDraft = places.firstOrNull { draft -> draft.id == targetPlaceId } ?: return null
    if (source.widgetIds.size != 1) {
        return null
    }
    return places
        .map { draft ->
            if (draft.id == targetPlaceId) draft.copy(widgetIds = draft.widgetIds + source.widgetIds) else draft
        }
        .filterNot { draft -> draft.id == sourcePlaceId }
}

/** [places] with [widgetId] moved out of [placeId] into a new place before [targetPlaceId], or at the end when null. */
fun splitDraftPage(places: List<PlaceDraft>, placeId: Long, widgetId: Long, targetPlaceId: Long?): List<PlaceDraft>? {
    val place: PlaceDraft = places.firstOrNull { draft -> draft.id == placeId } ?: return null
    if (widgetId !in place.widgetIds) {
        return null
    }
    val remainingWidgetIds: List<Long> = place.widgetIds - widgetId
    val withoutPage: List<PlaceDraft> = places.mapNotNull { draft ->
        when {
            draft.id != placeId -> draft
            remainingWidgetIds.isEmpty() -> null
            else -> draft.copy(widgetIds = remainingWidgetIds)
        }
    }
    val targetIndex: Int = targetPlaceId?.let { id ->
        withoutPage.indexOfFirst { draft -> draft.id == id }
    } ?: -1
    val insertionIndex: Int = if (targetIndex < 0) withoutPage.size else targetIndex
    val splitPlace: PlaceDraft = PlaceDraft(placeId = null, widgetIds = listOf(widgetId))
    return withoutPage.toMutableList().apply { add(insertionIndex, splitPlace) }
}

/** [places] with [widgetId] removed from [placeId]; a place left without pages is dropped. */
fun removeDraftPage(places: List<PlaceDraft>, placeId: Long, widgetId: Long): List<PlaceDraft>? {
    val place: PlaceDraft = places.firstOrNull { draft -> draft.id == placeId } ?: return null
    if (widgetId !in place.widgetIds) {
        return null
    }
    val remaining: List<Long> = place.widgetIds - widgetId
    return if (remaining.isEmpty()) {
        places - place
    } else {
        places.map { draft -> if (draft.id == placeId) draft.copy(widgetIds = remaining) else draft }
    }
}

/** [places] without [placeId], or null when it is not in the draft. */
fun removeDraftPlace(places: List<PlaceDraft>, placeId: Long): List<PlaceDraft>? =
    if (places.none { draft -> draft.id == placeId }) null else places.filterNot { draft -> draft.id == placeId }

/** [places] with page [fromIndex] of [placeId] moved to [toIndex], or null when either index is outside it. */
fun moveDraftPage(places: List<PlaceDraft>, placeId: Long, fromIndex: Int, toIndex: Int): List<PlaceDraft>? {
    val place: PlaceDraft = places.firstOrNull { draft -> draft.id == placeId } ?: return null
    if (fromIndex !in place.widgetIds.indices || toIndex !in place.widgetIds.indices) {
        return null
    }
    if (fromIndex == toIndex) {
        return places
    }
    val reordered: List<Long> = place.widgetIds.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    return places.map { draft -> if (draft.id == placeId) draft.copy(widgetIds = reordered) else draft }
}

/** [places] with the place at [fromIndex] moved to [toIndex], or null when either index is outside the draft. */
fun moveDraftPlace(places: List<PlaceDraft>, fromIndex: Int, toIndex: Int): List<PlaceDraft>? {
    if (fromIndex !in places.indices || toIndex !in places.indices) {
        return null
    }
    if (fromIndex == toIndex) {
        return places
    }
    return places.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

/**
 * The page and height [place] keeps after its pages change, or null when it has no pages left.
 * A lone Header stores no height; every other place is clamped to the resize bounds of its pages.
 */
fun normalizePlace(
    place: WidgetPlaceRecord,
    widgets: List<WidgetRecord>,
    providerInfo: (WidgetRecord) -> WidgetProviderInfo?
): WidgetPlaceLayout? = when {
    widgets.isEmpty() -> null

    widgets.size == 1 -> {
        val widget: WidgetRecord = widgets.single()
        WidgetPlaceLayout(
            currentPage = 0,
            heightDp = if (widget.kind == WidgetKind.HEADER) {
                null
            } else {
                place.heightDp?.let { heightDp ->
                    widget.resizeBounds(providerInfo).clamp(heightDp = heightDp, homeHeightDp = Int.MAX_VALUE)
                }
            }
        )
    }

    else -> {
        val bounds: ResizeBounds = ResizeBounds.stackBounds(widgets.map { widget -> widget.resizeBounds(providerInfo) })
        WidgetPlaceLayout(
            currentPage = place.currentPage.coerceIn(0, widgets.lastIndex),
            heightDp = place.heightDp?.let { heightDp -> bounds.clamp(heightDp = heightDp, homeHeightDp = Int.MAX_VALUE) }
        )
    }
}

/**
 * The height [place] renders at, clamped to its pages' resize bounds.
 * A Pop-up fills a missing stored height with the page default; the Widget Area keeps it unset.
 */
fun projectedHeightDp(
    place: WidgetPlace,
    headerHeightDp: Int?,
    homeHeightDp: Int?,
    providerInfo: (PlacedWidget.AppWidget) -> WidgetProviderInfo?
): Int? {
    if (place.widgets.singleOrNull() is PlacedWidget.Header) {
        return place.heightDp
    }
    val fillHeight: Boolean = place.ownerKey != null
    val bounds: ResizeBounds = place.widgets.stackResizeBounds(headerHeightDp = headerHeightDp, providerInfo = providerInfo)
        ?: return place.heightDp
    val storedHeightDp: Int? = place.heightDp
    val fallbackHeightDp: Int = place.widgets.resolvedDefaultHeightDp(bounds = bounds, providerInfo = providerInfo)
    return when {
        place.isStack -> {
            val base: Int = storedHeightDp ?: fallbackHeightDp
            if (homeHeightDp == null) base else bounds.clamp(heightDp = base, homeHeightDp = homeHeightDp)
        }

        fillHeight -> bounds.clamp(heightDp = storedHeightDp ?: fallbackHeightDp, homeHeightDp = homeHeightDp ?: Int.MAX_VALUE)

        storedHeightDp != null && bounds.isResizable && homeHeightDp != null ->
            bounds.clamp(heightDp = storedHeightDp, homeHeightDp = homeHeightDp)

        else -> storedHeightDp
    }
}

/** Resolves DAO rows into the Widget Area's display model, keeping only the first Header. */
internal fun projectPlaces(
    places: List<WidgetPlaceRecord>,
    widgets: List<WidgetRecord>,
    availability: (WidgetRecord) -> WidgetAvailability = { WidgetAvailability.Available }
): WidgetAreaResolution {
    val widgetsByPlace: Map<Long, List<WidgetRecord>> = widgets.groupBy { widget -> widget.placeId }
    var hasHeader: Boolean = false
    val duplicateHeaderIds: MutableList<Long> = mutableListOf()
    val resolvedPlaces: List<WidgetPlace> = places
        .sortedWith(compareBy({ place -> place.position }, { place -> place.id }))
        .mapNotNull { place ->
            val placedWidgets: List<PlacedWidget> = widgetsByPlace[place.id].orEmpty()
                .sortedWith(compareBy({ widget -> widget.pagePosition }, { widget -> widget.id }))
                .mapNotNull { widget ->
                    if (widget.kind == WidgetKind.HEADER) {
                        if (hasHeader) {
                            duplicateHeaderIds.add(widget.id)
                            return@mapNotNull null
                        }
                        hasHeader = true
                    }
                    widget.toPlacedWidget(availability = availability)
                }
            if (placedWidgets.isEmpty()) {
                null
            } else {
                WidgetPlace(
                    id = place.id,
                    heightDp = place.heightDp,
                    currentPage = place.currentPage.coerceIn(0, placedWidgets.lastIndex),
                    widgets = placedWidgets,
                    ownerKey = place.ownerKey
                )
            }
        }
    return WidgetAreaResolution(places = resolvedPlaces, duplicateHeaderIds = duplicateHeaderIds)
}

internal data class WidgetAreaResolution(val places: List<WidgetPlace>, val duplicateHeaderIds: List<Long>)

/** Applies [normalizePlace] to the stored rows of [placeId] inside the caller's transaction. */
internal suspend fun applyPlaceLayout(dao: WidgetAreaDao, host: IAppWidgetHostSource, placeId: Long) {
    val place: WidgetPlaceRecord = dao.getPlace(placeId = placeId) ?: return
    val widgets: List<WidgetRecord> = dao.getWidgetsForPlace(placeId = placeId)
    val layout: WidgetPlaceLayout = normalizePlace(place, widgets) { widget ->
        widget.appWidgetId?.let { id -> host.providerInfo(AppWidgetId(id)) }
    } ?: run {
        dao.deleteEmptyPlaces()
        return
    }
    if (layout.currentPage != place.currentPage) {
        dao.setCurrentPage(placeId = placeId, page = layout.currentPage)
    }
    if (layout.heightDp != place.heightDp) {
        dao.setPlaceHeight(placeId = placeId, heightDp = layout.heightDp)
    }
}

private fun WidgetRecord.resizeBounds(providerInfo: (WidgetRecord) -> WidgetProviderInfo?): ResizeBounds = appWidgetResizeBounds(
    providerInfo = if (kind == WidgetKind.APP_WIDGET) providerInfo(this) else null,
    defaultHeightDp = defaultHeightDp
)

private fun WidgetRecord.toPlacedWidget(availability: (WidgetRecord) -> WidgetAvailability): PlacedWidget? = when (kind) {
    WidgetKind.HEADER -> PlacedWidget.Header(id = id)

    WidgetKind.APP_WIDGET -> {
        val storedAppWidgetId: Int? = appWidgetId
        val storedProvider: WidgetProvider? = provider?.let(WidgetProvider::fromKey)
        val storedProfileSerial: Long? = profileSerial
        if (storedAppWidgetId == null || storedProvider == null || storedProfileSerial == null) {
            null
        } else {
            PlacedWidget.AppWidget(
                id = id,
                appWidgetId = storedAppWidgetId,
                provider = storedProvider,
                profileSerial = storedProfileSerial,
                extendsUnderRail = extendsUnderRail,
                availability = availability(this),
                defaultHeightDp = defaultHeightDp
            )
        }
    }

    WidgetKind.UNKNOWN -> null
}
