package core.data.testing

import core.data.widgets.AddAppWidget
import core.data.widgets.AddedAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import core.data.widgets.placeOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The mutable places and recorded writes behind a [createWidgetAreaRepositoryMock] repository. */
class WidgetAreaRepositoryState(initialPlaces: List<WidgetPlace> = emptyList()) {
    val places: MutableStateFlow<List<WidgetPlace>> = MutableStateFlow(initialPlaces)
    private var nextPlaceId: Long = (initialPlaces.maxOfOrNull { place -> place.id } ?: 0L) + 1L
    private var nextWidgetId: Long = (initialPlaces.flatMap { place -> place.widgets }.maxOfOrNull { widget -> widget.id } ?: 0L) + 1L

    val currentPageUpdates: MutableList<Pair<Long, Int>> = mutableListOf()

    /** The Widget Area places, excluding the places owned by a Pop-up. */
    val areaPlaces: Flow<List<WidgetPlace>> = places.map { current -> current.filter { place -> place.ownerKey == null } }
        .distinctUntilChanged()

    /** The places owned by [ownerKey], or the Widget Area when it is null. */
    fun placesFor(ownerKey: String?): Flow<List<WidgetPlace>> =
        places.map { current -> current.filter { place -> place.ownerKey == ownerKey } }.distinctUntilChanged()

    suspend fun addAppWidget(request: AddAppWidget): AddedAppWidget {
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
        val placeId: Long = when {
            ownerKey != null -> appendOwnedWidget(ownerKey = ownerKey, widget = widget)
            request.insertAt is InsertPosition.IntoStack -> (request.insertAt as InsertPosition.IntoStack).placeId
            else -> insertPlace(widget = widget)
        }
        return AddedAppWidget(widgetId = widgetId, placeId = placeId)
    }

    suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val place: WidgetPlace = places.value.placeOf(widgetId = widgetId) ?: return null
        val widget: PlacedWidget = place.widgets.first { candidate -> candidate.id == widgetId }
        val remaining: List<PlacedWidget> = place.widgets.filterNot { candidate -> candidate.id == widgetId }
        if (remaining.isEmpty()) {
            places.update { current -> current - place }
        } else {
            val currentPage: Int = place.currentPage.coerceIn(0, remaining.lastIndex)
            places.update { current -> current.replacing(place = place, widgets = remaining, currentPage = currentPage) }
            setCurrentPage(placeId = place.id, page = currentPage)
        }
        return (widget as? PlacedWidget.AppWidget)?.appWidgetId?.let(::AppWidgetId)
    }

    suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        places.update { current -> current.map { place -> if (place.id == placeId) place.copy(heightDp = heightDp) else place } }
    }

    suspend fun setCurrentPage(placeId: Long, page: Int) {
        currentPageUpdates.add(placeId to page)
    }

    private fun appendOwnedWidget(ownerKey: String, widget: PlacedWidget): Long {
        val existing: WidgetPlace? = places.value.firstOrNull { place -> place.ownerKey == ownerKey }
        if (existing == null) {
            return insertPlace(widget = widget, ownerKey = ownerKey)
        }
        places.update { current ->
            current.replacing(place = existing, widgets = existing.widgets + widget, currentPage = existing.widgets.size)
        }
        return existing.id
    }

    private fun insertPlace(widget: PlacedWidget, ownerKey: String? = null): Long {
        val place: WidgetPlace = WidgetPlace(
            id = nextPlaceId++,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(widget),
            ownerKey = ownerKey
        )
        places.update { current -> current + place }
        return place.id
    }
}

/** A MockK [IWidgetAreaRepository] over [state]. */
fun createWidgetAreaRepositoryMock(
    state: WidgetAreaRepositoryState = WidgetAreaRepositoryState(),
    popupOwnerKeysFlow: Flow<List<String>> = MutableStateFlow(emptyList()),
    removeOwnerPlacesAnswer: suspend (String) -> List<AppWidgetId> = { emptyList() }
): IWidgetAreaRepository {
    val mockWidgetAreaRepository: IWidgetAreaRepository = mockk()
    every { mockWidgetAreaRepository.places } returns state.areaPlaces
    every { mockWidgetAreaRepository.popupOwnerKeys } returns popupOwnerKeysFlow
    every { mockWidgetAreaRepository.placesFor(any()) } answers { state.placesFor(ownerKey = firstArg()) }
    every { mockWidgetAreaRepository.popupPlace(any()) } answers {
        val ownerKey: String = firstArg()
        state.placesFor(ownerKey = ownerKey).map { places -> places.firstOrNull() }
    }
    coEvery { mockWidgetAreaRepository.removeHeader() } returns null
    coEvery { mockWidgetAreaRepository.removeHeaderPage(any()) } returns null
    coEvery { mockWidgetAreaRepository.restoreHeader(any(), any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.replaceHeader(any()) } returns null
    coEvery { mockWidgetAreaRepository.undoReplaceHeader(any()) } just Runs
    coEvery { mockWidgetAreaRepository.setExtendsUnderRail(any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.addAppWidget(any()) } coAnswers { state.addAppWidget(request = firstArg()) }
    coEvery { mockWidgetAreaRepository.removeWidget(any()) } coAnswers { state.removeWidget(widgetId = firstArg()) }
    coEvery { mockWidgetAreaRepository.removeOwnerPlaces(any()) } coAnswers { removeOwnerPlacesAnswer(firstArg()) }
    coEvery { mockWidgetAreaRepository.setPlaceHeight(any(), any()) } coAnswers {
        state.setPlaceHeight(placeId = firstArg(), heightDp = arg(1))
    }
    coEvery { mockWidgetAreaRepository.setCurrentPage(any(), any()) } coAnswers {
        state.setCurrentPage(placeId = firstArg(), page = arg(1))
    }
    coEvery { mockWidgetAreaRepository.commitEdit(any()) } returns emptyList()
    return mockWidgetAreaRepository
}

private fun List<WidgetPlace>.replacing(
    place: WidgetPlace,
    widgets: List<PlacedWidget>,
    currentPage: Int = place.currentPage
): List<WidgetPlace> = map { current ->
    if (current.id == place.id) current.copy(widgets = widgets, currentPage = currentPage) else current
}
