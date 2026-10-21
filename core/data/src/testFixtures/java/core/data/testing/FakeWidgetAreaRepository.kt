package core.data.testing

import core.data.widgets.IWidgetAreaRepository
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
}
