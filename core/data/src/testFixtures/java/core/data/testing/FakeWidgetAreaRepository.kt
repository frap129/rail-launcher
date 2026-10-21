package core.data.testing

import core.data.widgets.AddAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.HeaderRemoval
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetAreaRepository(initialPlaces: List<WidgetPlace> = emptyList()) : IWidgetAreaRepository {
    private val storedPlaces: MutableStateFlow<List<WidgetPlace>> = MutableStateFlow(initialPlaces)
    private var nextPlaceId: Long = (initialPlaces.maxOfOrNull { place -> place.id } ?: 0L) + 1
    private var nextWidgetId: Long = (initialPlaces.flatMap { place -> place.widgets }.maxOfOrNull { widget -> widget.id } ?: 0L) + 1

    val removedHeaders: MutableList<Long?> = mutableListOf()
    val restoredHeaders: MutableList<HeaderRemoval> = mutableListOf()
    val addedAppWidgets: MutableList<AddAppWidget> = mutableListOf()
    val heightUpdates: MutableList<Pair<Long, Int?>> = mutableListOf()
    val currentPageUpdates: MutableList<Pair<Long, Int>> = mutableListOf()
    val edits: MutableList<WidgetAreaEdit> = mutableListOf()

    override val places: Flow<List<WidgetPlace>> = storedPlaces

    override suspend fun removeHeader(): HeaderRemoval? = removeHeaderPageInternal(widgetId = null)

    override suspend fun removeHeaderPage(widgetId: Long): HeaderRemoval? = removeHeaderPageInternal(widgetId = widgetId)

    private suspend fun removeHeaderPageInternal(widgetId: Long?): HeaderRemoval? {
        val placeIndex: Int = storedPlaces.value.indexOfFirst { place ->
            place.widgets.any { widget -> widget is PlacedWidget.Header && (widgetId == null || widget.id == widgetId) }
        }
        if (placeIndex < 0) {
            return null
        }
        val place: WidgetPlace = storedPlaces.value[placeIndex]
        val header: PlacedWidget.Header = place.widgets
            .filterIsInstance<PlacedWidget.Header>()
            .first { widget -> widgetId == null || widget.id == widgetId }
        val pagePosition: Int = place.widgets.indexOf(header)
        removedHeaders.add(widgetId)
        val remaining: List<PlacedWidget> = place.widgets.filterNot { widget -> widget.id == header.id }
        storedPlaces.update { places ->
            if (remaining.isEmpty()) {
                places - place
            } else {
                places.map { current -> if (current.id == place.id) current.dissolved(remaining) else current }
            }
        }
        return HeaderRemoval(position = placeIndex, placeId = place.id, pagePosition = pagePosition)
    }

    override suspend fun restoreHeader(position: Int, placeId: Long?, pagePosition: Int) {
        restoredHeaders.add(HeaderRemoval(position = position, placeId = placeId ?: 0L, pagePosition = pagePosition))
        val hasHeader: Boolean = storedPlaces.value.any { place ->
            place.widgets.any { widget -> widget is PlacedWidget.Header }
        }
        if (hasHeader) {
            return
        }
        val header: PlacedWidget.Header = PlacedWidget.Header(id = nextWidgetId++)
        if (placeId != null) {
            val target: WidgetPlace? = storedPlaces.value.firstOrNull { place -> place.id == placeId }
            if (target != null) {
                val page: Int = pagePosition.coerceIn(0, target.widgets.size)
                val widgets: List<PlacedWidget> = target.widgets.toMutableList().apply { add(page, header) }
                storedPlaces.update { places ->
                    places.map { place ->
                        if (place.id == target.id) place.copy(widgets = widgets, currentPage = page) else place
                    }
                }
                return
            }
        }
        val targetPosition: Int = position.coerceIn(0, storedPlaces.value.size)
        val headerPlace: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(header)
        )
        storedPlaces.update { places ->
            places.toMutableList().apply { add(targetPosition, headerPlace) }
        }
    }

    override suspend fun addAppWidget(request: AddAppWidget): Long {
        addedAppWidgets.add(request)
        val widgetId: Long = nextWidgetId++
        val widget: PlacedWidget.AppWidget = PlacedWidget.AppWidget(
            id = widgetId,
            appWidgetId = request.appWidgetId.value,
            provider = request.provider,
            profileSerial = request.profileSerial,
            extendsUnderRail = false,
            defaultHeightDp = request.defaultHeightDp
        )
        when (val insertAt: InsertPosition = request.insertAt) {
            is InsertPosition.IntoStack -> {
                val targetIndex: Int = storedPlaces.value.indexOfFirst { place -> place.id == insertAt.placeId }
                if (targetIndex < 0) {
                    return widgetId
                }
                storedPlaces.update { places ->
                    places.mapIndexed { index, place ->
                        if (index == targetIndex) {
                            place.copy(widgets = place.widgets + widget, currentPage = place.widgets.size)
                        } else {
                            place
                        }
                    }
                }
            }

            is InsertPosition.Below -> {
                val targetIndex: Int = storedPlaces.value.indexOfFirst { place -> place.id == insertAt.placeId }
                    .takeIf { index -> index >= 0 }
                    ?.plus(1)
                    ?: storedPlaces.value.size
                insertPlace(targetIndex = targetIndex, widget = widget)
            }

            InsertPosition.End -> insertPlace(targetIndex = storedPlaces.value.size, widget = widget)
        }
        return widgetId
    }

    private fun insertPlace(targetIndex: Int, widget: PlacedWidget) {
        val place: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(widget)
        )
        storedPlaces.update { places -> places.toMutableList().apply { add(targetIndex, place) } }
    }

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val place: WidgetPlace = storedPlaces.value.firstOrNull { place ->
            place.widgets.any { widget -> widget.id == widgetId }
        } ?: return null
        val widget: PlacedWidget = place.widgets.first { candidate -> candidate.id == widgetId }
        val remaining: List<PlacedWidget> = place.widgets.filterNot { candidate -> candidate.id == widgetId }
        storedPlaces.update { places ->
            if (remaining.isEmpty()) {
                places - place
            } else {
                places.map { current -> if (current.id == place.id) current.dissolved(remaining) else current }
            }
        }
        return (widget as? PlacedWidget.AppWidget)?.appWidgetId?.let(::AppWidgetId)
    }

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        heightUpdates.add(placeId to heightDp)
        storedPlaces.update { places ->
            places.map { place -> if (place.id == placeId) place.copy(heightDp = heightDp) else place }
        }
    }

    override suspend fun setCurrentPage(placeId: Long, page: Int) {
        currentPageUpdates.add(placeId to page)
        storedPlaces.update { places ->
            places.map { place ->
                if (place.id == placeId) place.copy(currentPage = page.coerceIn(0, place.widgets.lastIndex)) else place
            }
        }
    }

    override suspend fun commitEdit(edit: WidgetAreaEdit): List<AppWidgetId> {
        edits.add(edit)
        val allWidgets: Map<Long, PlacedWidget> = storedPlaces.value
            .flatMap { place -> place.widgets }
            .associateBy { widget -> widget.id }
        val removedIds: Set<Long> = edit.removedWidgetIds
        val retainedPlaceIds: Set<Long> = edit.places.mapNotNull { draft -> draft.placeId }.toSet()
        val orphanPlaces: List<WidgetPlace> = storedPlaces.value.filter { place -> place.id !in retainedPlaceIds }
        val freedIds: List<AppWidgetId> = (
            storedPlaces.value.flatMap { place -> place.widgets } +
                orphanPlaces.flatMap { place -> place.widgets }
            )
            .filter { widget -> widget.id in removedIds || orphanPlaces.any { place -> place.id in place.widgets.map { it.id } } }
            .filterIsInstance<PlacedWidget.AppWidget>()
            .map { widget -> AppWidgetId(widget.appWidgetId) }
        var nextId: Long = nextPlaceId
        val updated: List<WidgetPlace> = edit.places.mapNotNull { draft ->
            val existing: WidgetPlace? = draft.placeId?.let { id -> storedPlaces.value.firstOrNull { place -> place.id == id } }
            val widgets: List<PlacedWidget> = draft.widgetIds.mapNotNull { widgetId ->
                allWidgets[widgetId]?.takeUnless { widget -> widget.id in removedIds }
            }
            if (widgets.isEmpty()) {
                null
            } else {
                WidgetPlace(
                    id = draft.placeId ?: nextId++,
                    heightDp = existing?.heightDp,
                    currentPage = (existing?.currentPage ?: 0).coerceIn(0, widgets.lastIndex),
                    widgets = widgets
                ).dissolved(widgets)
            }
        }
        nextPlaceId = nextId
        storedPlaces.value = updated
        return freedIds.distinct()
    }
}

private fun WidgetPlace.dissolved(widgets: List<PlacedWidget>): WidgetPlace {
    if (widgets.size > 1) {
        return copy(widgets = widgets, currentPage = currentPage.coerceIn(0, widgets.lastIndex))
    }
    val widget: PlacedWidget = widgets.first()
    return copy(
        widgets = widgets,
        currentPage = 0,
        heightDp = if (widget is PlacedWidget.Header) null else heightDp
    )
}
