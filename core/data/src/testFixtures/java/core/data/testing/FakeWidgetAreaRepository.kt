package core.data.testing

import core.data.widgets.AddAppWidget
import core.data.widgets.AddedAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.HeaderRemoval
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.ReplaceHeaderUndo
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetPlace
import core.data.widgets.placeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeWidgetAreaRepository(initialPlaces: List<WidgetPlace> = emptyList()) : IWidgetAreaRepository {
    private val storedPlaces: MutableStateFlow<List<WidgetPlace>> = MutableStateFlow(initialPlaces)
    private var nextPlaceId: Long = (initialPlaces.maxOfOrNull { place -> place.id } ?: 0L) + 1
    private var nextWidgetId: Long = (initialPlaces.flatMap { place -> place.widgets }.maxOfOrNull { widget -> widget.id } ?: 0L) + 1

    val removedHeaders: MutableList<Long?> = mutableListOf()
    val restoredHeaders: MutableList<HeaderRemoval> = mutableListOf()
    val replacedWidgetIds: MutableList<Long> = mutableListOf()
    val undoneReplaceHeaders: MutableList<ReplaceHeaderUndo> = mutableListOf()
    val extendsUnderRailUpdates: MutableList<Pair<Long, Boolean>> = mutableListOf()
    val addedAppWidgets: MutableList<AddAppWidget> = mutableListOf()
    val heightUpdates: MutableList<Pair<Long, Int?>> = mutableListOf()
    val currentPageUpdates: MutableList<Pair<Long, Int>> = mutableListOf()
    val edits: MutableList<WidgetAreaEdit> = mutableListOf()

    /** Fails the next [removeOwnerPlaces] call instead of removing. */
    val failNextRemoveOwnerPlaces: FailOnce = FailOnce()

    override val places: Flow<List<WidgetPlace>> = placesFor(ownerKey = null)

    override fun placesFor(ownerKey: String?): Flow<List<WidgetPlace>> =
        storedPlaces.map { places -> places.filter { place -> place.ownerKey == ownerKey } }

    override val popupOwnerKeys: Flow<List<String>> = storedPlaces.map { places ->
        places.mapNotNull { place -> place.ownerKey }.distinct()
    }

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

    override suspend fun replaceHeader(widgetId: Long): ReplaceHeaderUndo? {
        val places: List<WidgetPlace> = storedPlaces.value
        val headerPlaceIndex: Int = places.indexOfFirst { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }
        val widgetPlace: WidgetPlace = places.placeOf(widgetId = widgetId) ?: return null
        val widgetPlaceIndex: Int = places.indexOf(widgetPlace)
        if (headerPlaceIndex < 0 || headerPlaceIndex == widgetPlaceIndex) {
            return null
        }
        val headerPlace: WidgetPlace = places[headerPlaceIndex]
        val header: PlacedWidget.Header = headerPlace.widgets.filterIsInstance<PlacedWidget.Header>().first()
        val widget: PlacedWidget.AppWidget = widgetPlace.widgets
            .filterIsInstance<PlacedWidget.AppWidget>()
            .firstOrNull { candidate -> candidate.id == widgetId }
            ?: return null
        val headerPagePosition: Int = headerPlace.widgets.indexOf(header)
        val targetIsStack: Boolean = headerPlace.widgets.size > 1
        val updatedHeaderPlace: WidgetPlace = headerPlace.copy(
            widgets = headerPlace.widgets.map { page -> if (page.id == header.id) widget else page },
            currentPage = headerPagePosition,
            heightDp = if (targetIsStack) headerPlace.heightDp else widgetPlace.heightDp
        )
        val remainingWidgets: List<PlacedWidget> = widgetPlace.widgets.filterNot { page -> page.id == widget.id }
        val updatedPlaces: List<WidgetPlace> = places
            .mapIndexed { index, place ->
                when (index) {
                    headerPlaceIndex -> updatedHeaderPlace
                    widgetPlaceIndex -> if (remainingWidgets.isEmpty()) place else place.dissolved(remainingWidgets)
                    else -> place
                }
            }
            .filterNot { place -> place.widgets.isEmpty() }
        storedPlaces.value = updatedPlaces
        replacedWidgetIds.add(widgetId)
        return ReplaceHeaderUndo(
            widgetId = widget.id,
            headerPlaceId = headerPlace.id,
            headerPosition = headerPlaceIndex,
            headerPagePosition = headerPagePosition,
            headerHeightDp = headerPlace.heightDp,
            headerCurrentPage = headerPlace.currentPage,
            widgetPlaceId = widgetPlace.id,
            widgetPosition = widgetPlaceIndex,
            widgetPagePosition = widgetPlace.widgets.indexOf(widget),
            widgetHeightDp = widgetPlace.heightDp,
            widgetCurrentPage = widgetPlace.currentPage
        )
    }

    override suspend fun undoReplaceHeader(undo: ReplaceHeaderUndo) {
        undoneReplaceHeaders.add(undo)
        val updated: MutableList<WidgetPlace> = storedPlaces.value.toMutableList()
        val widget: PlacedWidget.AppWidget? = updated
            .flatMap { place -> place.widgets }
            .filterIsInstance<PlacedWidget.AppWidget>()
            .firstOrNull { candidate -> candidate.id == undo.widgetId }
        if (widget != null) {
            val currentPlace: WidgetPlace? = updated.placeOf(widgetId = widget.id)
            if (currentPlace != null) {
                val currentIndex: Int = updated.indexOf(currentPlace)
                val remaining: List<PlacedWidget> = currentPlace.widgets.filterNot { page -> page.id == widget.id }
                if (remaining.isEmpty()) {
                    updated.removeAt(currentIndex)
                } else {
                    updated[currentIndex] = currentPlace.dissolved(remaining)
                }
            }
            val widgetPlaceIndex: Int = updated.indexOfFirst { place -> place.id == undo.widgetPlaceId }
            if (widgetPlaceIndex >= 0) {
                val place: WidgetPlace = updated[widgetPlaceIndex]
                val pages: MutableList<PlacedWidget> = place.widgets.toMutableList()
                pages.add(index = undo.widgetPagePosition.coerceIn(0, pages.size), element = widget)
                updated[widgetPlaceIndex] = place.copy(
                    widgets = pages,
                    heightDp = undo.widgetHeightDp,
                    currentPage = undo.widgetCurrentPage.coerceIn(0, pages.lastIndex)
                )
            } else {
                updated.add(
                    undo.widgetPosition.coerceIn(0, updated.size),
                    WidgetPlace(
                        id = undo.widgetPlaceId,
                        heightDp = undo.widgetHeightDp,
                        currentPage = undo.widgetCurrentPage.coerceAtLeast(0),
                        widgets = listOf(widget)
                    )
                )
            }
        }
        val hasHeader: Boolean = updated.any { place -> place.widgets.any { page -> page is PlacedWidget.Header } }
        if (!hasHeader) {
            val headerPlaceIndex: Int = updated.indexOfFirst { place -> place.id == undo.headerPlaceId }
            val header: PlacedWidget.Header = PlacedWidget.Header(id = nextWidgetId++)
            if (headerPlaceIndex >= 0) {
                val place: WidgetPlace = updated[headerPlaceIndex]
                val pages: MutableList<PlacedWidget> = place.widgets.toMutableList()
                pages.add(index = undo.headerPagePosition.coerceIn(0, pages.size), element = header)
                updated[headerPlaceIndex] = place.copy(
                    widgets = pages,
                    heightDp = undo.headerHeightDp,
                    currentPage = undo.headerCurrentPage.coerceIn(0, pages.lastIndex)
                )
            } else {
                updated.add(
                    undo.headerPosition.coerceIn(0, updated.size),
                    WidgetPlace(
                        id = undo.headerPlaceId,
                        heightDp = undo.headerHeightDp,
                        currentPage = undo.headerCurrentPage.coerceAtLeast(0),
                        widgets = listOf(header)
                    )
                )
            }
        }
        storedPlaces.value = updated
    }

    override suspend fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean) {
        val place: WidgetPlace? = storedPlaces.value.placeOf(widgetId = widgetId)
        if (place?.isStack == true) {
            return
        }
        extendsUnderRailUpdates.add(widgetId to extendsUnderRail)
        storedPlaces.update { places ->
            places.map { place ->
                place.copy(
                    widgets = place.widgets.map { widget ->
                        if (widget is PlacedWidget.AppWidget && widget.id == widgetId) {
                            widget.copy(extendsUnderRail = extendsUnderRail)
                        } else {
                            widget
                        }
                    }
                )
            }
        }
    }

    override suspend fun addAppWidget(request: AddAppWidget): AddedAppWidget {
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
        val ownerKey: String? = request.ownerKey
        if (ownerKey != null) {
            val existing: WidgetPlace? = storedPlaces.value.firstOrNull { place -> place.ownerKey == ownerKey }
            val placeId: Long = if (existing == null) {
                insertPlace(targetIndex = storedPlaces.value.size, widget = widget, ownerKey = ownerKey)
            } else {
                storedPlaces.update { places ->
                    places.map { place ->
                        if (place.id == existing.id) {
                            place.copy(widgets = place.widgets + widget, currentPage = place.widgets.size)
                        } else {
                            place
                        }
                    }
                }
                existing.id
            }
            return AddedAppWidget(widgetId = widgetId, placeId = placeId)
        }
        val placeId: Long = when (val insertAt: InsertPosition = request.insertAt) {
            is InsertPosition.IntoStack -> {
                val targetIndex: Int = storedPlaces.value.indexOfFirst { place -> place.id == insertAt.placeId }
                if (targetIndex >= 0) {
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
                insertAt.placeId
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
        return AddedAppWidget(widgetId = widgetId, placeId = placeId)
    }

    private fun insertPlace(targetIndex: Int, widget: PlacedWidget, ownerKey: String? = null): Long {
        val place: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(widget),
            ownerKey = ownerKey
        )
        storedPlaces.update { places -> places.toMutableList().apply { add(targetIndex, place) } }
        return place.id
    }

    override suspend fun removeOwnerPlaces(ownerKey: String): List<AppWidgetId> {
        failNextRemoveOwnerPlaces.throwIfPending()
        val ownedPlaces: List<WidgetPlace> = storedPlaces.value.filter { place -> place.ownerKey == ownerKey }
        if (ownedPlaces.isEmpty()) {
            return emptyList()
        }
        storedPlaces.update { places -> places.filterNot { place -> place.ownerKey == ownerKey } }
        return ownedPlaces
            .flatMap { place -> place.widgets }
            .filterIsInstance<PlacedWidget.AppWidget>()
            .map { widget -> AppWidgetId(widget.appWidgetId) }
    }

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val place: WidgetPlace = storedPlaces.value.placeOf(widgetId = widgetId) ?: return null
        val widget: PlacedWidget = place.widgets.first { candidate -> candidate.id == widgetId }
        val remaining: List<PlacedWidget> = place.widgets.filterNot { candidate -> candidate.id == widgetId }
        storedPlaces.update { places ->
            if (remaining.isEmpty()) {
                places - place
            } else {
                places.map { current -> if (current.id == place.id) current.dissolved(remaining) else current }
            }
        }
        if (remaining.isNotEmpty()) {
            setCurrentPage(placeId = place.id, page = place.currentPage.coerceIn(0, remaining.lastIndex))
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
