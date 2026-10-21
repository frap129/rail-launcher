package core.data.testing

import core.data.widgets.AddAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWidgetAreaRepository(initialPlaces: List<WidgetPlace> = emptyList()) : IWidgetAreaRepository {
    private val storedPlaces: MutableStateFlow<List<WidgetPlace>> = MutableStateFlow(initialPlaces)
    private var nextPlaceId: Long = (initialPlaces.maxOfOrNull { place -> place.id } ?: 0L) + 1
    private var nextWidgetId: Long = (initialPlaces.flatMap { place -> place.widgets }.maxOfOrNull { widget -> widget.id } ?: 0L) + 1

    val removedHeaders: MutableList<Unit> = mutableListOf()
    val restoredHeaderPositions: MutableList<Int> = mutableListOf()
    val addedAppWidgets: MutableList<AddAppWidget> = mutableListOf()
    val heightUpdates: MutableList<Pair<Long, Int?>> = mutableListOf()

    override val places: Flow<List<WidgetPlace>> = storedPlaces

    override suspend fun removeHeader(): Int? {
        val headerIndex: Int = storedPlaces.value.indexOfFirst { place ->
            place.widgets.any { widget -> widget is PlacedWidget.Header }
        }
        if (headerIndex < 0) {
            return null
        }
        val headerPlace: WidgetPlace = storedPlaces.value[headerIndex]
        removedHeaders.add(Unit)
        storedPlaces.update { places -> places - headerPlace }
        return headerIndex
    }

    override suspend fun restoreHeader(position: Int) {
        restoredHeaderPositions.add(position)
        val hasHeader: Boolean = storedPlaces.value.any { place ->
            place.widgets.any { widget -> widget is PlacedWidget.Header }
        }
        if (hasHeader) {
            return
        }
        val targetPosition: Int = position.coerceIn(0, storedPlaces.value.size)
        val headerPlace: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = nextWidgetId++))
        )
        storedPlaces.update { places ->
            places.toMutableList().apply { add(targetPosition, headerPlace) }
        }
    }

    override suspend fun addAppWidget(request: AddAppWidget): Long {
        addedAppWidgets.add(request)
        val targetIndex: Int = when (val insertAt: InsertPosition = request.insertAt) {
            is InsertPosition.Below -> storedPlaces.value.indexOfFirst { place -> place.id == insertAt.placeId }
                .takeIf { index -> index >= 0 }
                ?.plus(1)
                ?: storedPlaces.value.size

            InsertPosition.End -> storedPlaces.value.size
        }
        val widgetId: Long = nextWidgetId++
        val place: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(
                PlacedWidget.AppWidget(
                    id = widgetId,
                    appWidgetId = request.appWidgetId.value,
                    provider = request.provider,
                    profileSerial = request.profileSerial,
                    extendsUnderRail = false,
                    defaultHeightDp = request.defaultHeightDp
                )
            )
        )
        storedPlaces.update { places -> places.toMutableList().apply { add(targetIndex, place) } }
        return widgetId
    }

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val place: WidgetPlace = storedPlaces.value.firstOrNull { place ->
            place.widgets.any { widget -> widget.id == widgetId }
        } ?: return null
        val widget: PlacedWidget.AppWidget = place.widgets.filterIsInstance<PlacedWidget.AppWidget>()
            .firstOrNull { widget -> widget.id == widgetId }
            ?: return null
        val remaining: List<PlacedWidget> = place.widgets.filterNot { widget -> widget.id == widgetId }
        storedPlaces.update { places ->
            places.mapNotNull { current ->
                when {
                    current.id != place.id -> current
                    remaining.isEmpty() -> null
                    else -> current.copy(widgets = remaining)
                }
            }
        }
        return AppWidgetId(widget.appWidgetId)
    }

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        heightUpdates.add(placeId to heightDp)
        storedPlaces.update { places ->
            places.map { place -> if (place.id == placeId) place.copy(heightDp = heightDp) else place }
        }
    }
}
