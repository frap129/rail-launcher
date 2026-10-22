package core.data.testing

import core.data.room.dao.WidgetAreaDao
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetAreaDao(initialPlaces: List<WidgetPlaceRecord> = emptyList(), initialWidgets: List<WidgetRecord> = emptyList()) :
    WidgetAreaDao() {
    private val places: MutableStateFlow<List<WidgetPlaceRecord>> = MutableStateFlow(initialPlaces)
    private val widgets: MutableStateFlow<List<WidgetRecord>> = MutableStateFlow(initialWidgets)
    private var nextPlaceId: Long = (initialPlaces.maxOfOrNull { place -> place.id } ?: 0L) + 1
    private var nextWidgetId: Long = (initialWidgets.maxOfOrNull { widget -> widget.id } ?: 0L) + 1

    override fun observePlaces(): Flow<List<WidgetPlaceRecord>> = places

    override fun observeWidgets(): Flow<List<WidgetRecord>> = widgets

    override suspend fun getHeaders(): List<WidgetRecord> {
        val positionByPlaceId: Map<Long, Int> = places.value.associate { place -> place.id to place.position }
        return widgets.value
            .filter { widget -> widget.kind == WidgetKind.HEADER }
            .sortedWith(
                compareBy(
                    { widget -> positionByPlaceId[widget.placeId] ?: Int.MAX_VALUE },
                    { widget -> widget.pagePosition },
                    { widget -> widget.id }
                )
            )
    }

    override suspend fun getPlacePosition(placeId: Long): Int? = places.value.firstOrNull { place -> place.id == placeId }?.position

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        places.update { current -> current.map { place -> if (place.id == placeId) place.copy(heightDp = heightDp) else place } }
    }

    override suspend fun getPlace(placeId: Long): WidgetPlaceRecord? = places.value.firstOrNull { place -> place.id == placeId }

    override suspend fun getPlaceAtPosition(position: Int): WidgetPlaceRecord? =
        places.value.firstOrNull { place -> place.position == position }

    override suspend fun setCurrentPage(placeId: Long, page: Int) {
        places.update { current -> current.map { place -> if (place.id == placeId) place.copy(currentPage = page) else place } }
    }

    override suspend fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean) {
        widgets.update { current ->
            current.map { widget ->
                if (widget.id == widgetId && widget.kind == WidgetKind.APP_WIDGET) {
                    widget.copy(extendsUnderRail = extendsUnderRail)
                } else {
                    widget
                }
            }
        }
    }

    override suspend fun getWidget(widgetId: Long): WidgetRecord? = widgets.value.firstOrNull { widget -> widget.id == widgetId }

    override suspend fun getWidgets(ids: List<Long>): List<WidgetRecord> = widgets.value.filter { widget -> widget.id in ids }

    override suspend fun getWidgetsForPlace(placeId: Long): List<WidgetRecord> = widgets.value
        .filter { widget -> widget.placeId == placeId }
        .sortedWith(compareBy({ widget -> widget.pagePosition }, { widget -> widget.id }))

    override suspend fun getAppWidgets(): List<WidgetRecord> = widgets.value.filter { widget -> widget.kind == WidgetKind.APP_WIDGET }

    override suspend fun allAppWidgetIds(): List<Int> = widgets.value.mapNotNull { widget -> widget.appWidgetId }

    override suspend fun deleteWidgetsByAppWidgetIds(ids: List<Int>) {
        widgets.update { current -> current.filterNot { widget -> widget.appWidgetId in ids } }
    }

    override suspend fun insertPlace(place: WidgetPlaceRecord): Long {
        val id: Long = if (place.id == 0L) nextPlaceId++ else place.id
        places.update { current -> current + place.copy(id = id) }
        return id
    }

    override suspend fun insertWidget(widget: WidgetRecord): Long {
        val id: Long = if (widget.id == 0L) nextWidgetId++ else widget.id
        widgets.update { current -> current + widget.copy(id = id) }
        return id
    }

    override suspend fun deleteWidgets(ids: List<Long>) {
        widgets.update { current -> current.filterNot { widget -> widget.id in ids } }
    }

    override suspend fun deleteEmptyPlaces() {
        val placeIds: Set<Long> = widgets.value.mapTo(HashSet()) { widget -> widget.placeId }
        places.update { current -> current.filter { place -> place.id in placeIds } }
    }

    override suspend fun shiftPositionsFrom(position: Int) {
        places.update { current ->
            current.map { place -> if (place.position >= position) place.copy(position = place.position + 1) else place }
        }
    }

    override suspend fun getMaxPlacePosition(): Int = places.value.maxOfOrNull { place -> place.position } ?: -1

    override suspend fun getPlaceIds(): List<Long> = places.value.sortedBy { place -> place.position }.map { place -> place.id }

    override suspend fun appWidgetIdsForPlaces(placeIds: List<Long>): List<Int> = widgets.value
        .filter { widget -> widget.placeId in placeIds }
        .mapNotNull { widget -> widget.appWidgetId }

    override suspend fun appWidgetIdsForWidgets(widgetIds: List<Long>): List<Int> = widgets.value
        .filter { widget -> widget.id in widgetIds }
        .mapNotNull { widget -> widget.appWidgetId }

    override suspend fun moveWidget(widgetId: Long, placeId: Long, position: Int) {
        widgets.update { current ->
            current.map { widget ->
                if (widget.id == widgetId) widget.copy(placeId = placeId, pagePosition = position) else widget
            }
        }
    }

    override suspend fun deletePlaces(placeIds: List<Long>) {
        if (placeIds.isEmpty()) {
            return
        }
        widgets.update { current -> current.filterNot { widget -> widget.placeId in placeIds } }
        places.update { current -> current.filterNot { place -> place.id in placeIds } }
    }

    override suspend fun setPlacePosition(placeId: Long, position: Int) {
        places.update { current -> current.map { place -> if (place.id == placeId) place.copy(position = position) else place } }
    }
}
