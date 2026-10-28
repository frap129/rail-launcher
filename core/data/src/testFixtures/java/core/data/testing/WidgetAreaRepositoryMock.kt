package core.data.testing

import core.data.widgets.AddedAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.WidgetPlace
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The places a [createWidgetAreaRepositoryMock] repository reports, keyed by Pop-up owner key; null is the Widget Area. */
class WidgetAreaRepositoryState(initialPlacesByOwner: Map<String?, List<WidgetPlace>> = emptyMap()) {
    /** The places each owner holds, partitioned before the mock reports them. */
    val placesByOwner: MutableStateFlow<Map<String?, List<WidgetPlace>>> = MutableStateFlow(initialPlacesByOwner)

    /** The Widget Area places. */
    val areaPlaces: Flow<List<WidgetPlace>> = placesByOwner.map { places -> places[null].orEmpty() }.distinctUntilChanged()

    /** The places owned by [ownerKey], or the Widget Area when it is null. */
    fun placesFor(ownerKey: String?): Flow<List<WidgetPlace>> =
        placesByOwner.map { places -> places[ownerKey].orEmpty() }.distinctUntilChanged()

    /** The single place owned by the Pop-up [ownerKey], if it has one. */
    fun popupPlace(ownerKey: String): Flow<WidgetPlace?> =
        placesByOwner.map { places -> places[ownerKey]?.firstOrNull() }.distinctUntilChanged()
}

/** The ids a stubbed add answers with by default: the first Widget and place ids the database allocates. */
private val defaultAddedAppWidget: AddedAppWidget = AddedAppWidget(widgetId = 1L, placeId = 1L)

/** The mutable places with the mock over them. */
class WidgetAreaRepositoryFixture(val state: WidgetAreaRepositoryState, val mockWidgetAreaRepository: IWidgetAreaRepository)

/** A [WidgetAreaRepositoryFixture] holding [places] under the owner each names; null is the Widget Area. */
fun createWidgetAreaRepositoryFixture(places: List<WidgetPlace> = listOf(headerPlace)): WidgetAreaRepositoryFixture {
    val state: WidgetAreaRepositoryState = WidgetAreaRepositoryState(initialPlacesByOwner = places.groupBy { place -> place.ownerKey })
    return WidgetAreaRepositoryFixture(state = state, mockWidgetAreaRepository = createWidgetAreaRepositoryMock(state))
}

/** A MockK [IWidgetAreaRepository] holding [places], as a [createWidgetAreaRepositoryFixture] reports them. */
fun createWidgetAreaRepositoryMock(places: List<WidgetPlace>): IWidgetAreaRepository =
    createWidgetAreaRepositoryFixture(places = places).mockWidgetAreaRepository

/** A MockK [IWidgetAreaRepository] over [state], adding [addedAppWidget] and freeing no App Widgets. */
fun createWidgetAreaRepositoryMock(
    state: WidgetAreaRepositoryState = WidgetAreaRepositoryState(),
    popupOwnerKeysFlow: Flow<List<String>> = MutableStateFlow(emptyList()),
    removeOwnerPlacesAnswer: suspend (String) -> List<AppWidgetId> = { emptyList() },
    addedAppWidget: AddedAppWidget = defaultAddedAppWidget
): IWidgetAreaRepository {
    val mockWidgetAreaRepository: IWidgetAreaRepository = mockk()
    every { mockWidgetAreaRepository.places } returns state.areaPlaces
    every { mockWidgetAreaRepository.popupOwnerKeys } returns popupOwnerKeysFlow
    every { mockWidgetAreaRepository.placesFor(any()) } answers { state.placesFor(ownerKey = firstArg()) }
    every { mockWidgetAreaRepository.popupPlace(any()) } answers { state.popupPlace(ownerKey = firstArg()) }
    coEvery { mockWidgetAreaRepository.removeHeader() } returns null
    coEvery { mockWidgetAreaRepository.removeHeaderPage(any()) } returns null
    coEvery { mockWidgetAreaRepository.restoreHeader(any(), any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.replaceHeader(any()) } returns null
    coEvery { mockWidgetAreaRepository.undoReplaceHeader(any()) } just Runs
    coEvery { mockWidgetAreaRepository.setExtendsUnderRail(any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.addAppWidget(any()) } returns addedAppWidget
    coEvery { mockWidgetAreaRepository.removeWidget(any()) } returns null
    coEvery { mockWidgetAreaRepository.removeOwnerPlaces(any()) } coAnswers { removeOwnerPlacesAnswer(firstArg()) }
    coEvery { mockWidgetAreaRepository.setPlaceHeight(any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.setCurrentPage(any(), any()) } just Runs
    coEvery { mockWidgetAreaRepository.commitEdit(any()) } returns emptyList()
    return mockWidgetAreaRepository
}
