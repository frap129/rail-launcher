package feature.launcher.widgets

import core.data.widgets.PlaceDraft
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace

/** The pending layout of one Edit Widgets session. */
data class WidgetEditDraft(
    val places: List<PlaceDraft>,
    val removedWidgetIds: Set<Long> = emptySet(),
    val expandedPlaceIds: Set<Long> = emptySet()
) {
    val placeIds: List<Long> get() = places.mapNotNull { draft -> draft.placeId }
}

/** Resolves the draft against the live places, keeping removed widgets and vanished places out. */
internal fun WidgetEditDraft.toPlaces(source: List<WidgetPlace>): List<WidgetPlace> {
    val sourceById: Map<Long, WidgetPlace> = source.associateBy { place -> place.id }
    val sourceWidgets: Map<Long, PlacedWidget> = source
        .flatMap { place -> place.widgets }
        .associateBy { widget -> widget.id }
    val drafted: List<WidgetPlace> = places.mapNotNull { draft ->
        val widgets: List<PlacedWidget> = draft.widgetIds
            .mapNotNull { widgetId -> sourceWidgets[widgetId] }
            .filterNot { widget -> widget.id in removedWidgetIds }
        if (widgets.isEmpty()) {
            null
        } else {
            val base: WidgetPlace? = draft.placeId?.let { placeId -> sourceById[placeId] }
            if (base != null) {
                base.copy(widgets = widgets, currentPage = 0)
            } else {
                WidgetPlace(
                    id = -draft.widgetIds.first(),
                    heightDp = null,
                    currentPage = 0,
                    widgets = widgets
                )
            }
        }
    }
    val draftedPlaceIds: Set<Long> = places.mapNotNull { draft -> draft.placeId }.toSet()
    val appended: List<WidgetPlace> = source
        .filter { place -> place.id !in draftedPlaceIds }
        .mapNotNull { place ->
            val widgets: List<PlacedWidget> = place.widgets.filterNot { widget -> widget.id in removedWidgetIds }
            if (widgets.isEmpty()) null else place.copy(widgets = widgets, currentPage = 0)
        }
    return drafted + appended
}

/** Drops pages and places that vanished while the draft was open, and appends newly added places. */
internal fun WidgetEditDraft.reconciledWith(source: List<WidgetPlace>): WidgetEditDraft {
    val sourceById: Map<Long, WidgetPlace> = source.associateBy { place -> place.id }
    val knownPlaceIds: Set<Long> = places.mapNotNull { draft -> draft.placeId }.toSet()
    val keptPlaces: List<PlaceDraft> = places.mapNotNull { draft ->
        if (draft.placeId == null) {
            draft
        } else {
            val place: WidgetPlace = sourceById[draft.placeId] ?: return@mapNotNull null
            val sourceWidgetIds: Set<Long> = place.widgets.mapTo(HashSet()) { widget -> widget.id }
            val keptWidgetIds: List<Long> = draft.widgetIds
                .filter { widgetId -> widgetId in sourceWidgetIds }
                .filterNot { widgetId -> widgetId in removedWidgetIds }
            if (keptWidgetIds.isEmpty()) null else draft.copy(widgetIds = keptWidgetIds)
        }
    }
    val appendedPlaces: List<PlaceDraft> = source
        .filter { place -> place.id !in knownPlaceIds }
        .mapNotNull { place ->
            val widgetIds: List<Long> = place.widgets
                .map { widget -> widget.id }
                .filterNot { widgetId -> widgetId in removedWidgetIds }
            if (widgetIds.isEmpty()) null else PlaceDraft(placeId = place.id, widgetIds = widgetIds)
        }
    return copy(
        places = keptPlaces + appendedPlaces,
        expandedPlaceIds = expandedPlaceIds.intersect(keptPlaces.mapNotNull { draft -> draft.placeId }.toSet())
    )
}
