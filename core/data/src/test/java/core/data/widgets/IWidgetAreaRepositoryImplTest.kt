package core.data.widgets

import app.cash.turbine.test
import core.data.testing.FakeWidgetAreaDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IWidgetAreaRepositoryImplTest {
    @Test
    fun `orders places by position and widgets by page position`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 2L, position = 1),
                place(id = 1L, position = 0)
            ),
            initialWidgets = listOf(
                appWidget(id = 22L, placeId = 2L),
                header(id = 12L, placeId = 1L, pagePosition = 0),
                appWidget(id = 11L, placeId = 1L, pagePosition = 1)
            )
        )
        IWidgetAreaRepositoryImpl(dao).places.test {
            val places: List<WidgetPlace> = awaitItem()
            assertEquals(listOf(1L, 2L), places.map { place -> place.id })
            assertEquals(listOf(12L, 11L), places.first().widgets.map { widget -> widget.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops places without widgets`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(header(id = 2L, placeId = 2L, pagePosition = 0))
        )
        IWidgetAreaRepositoryImpl(dao).places.test {
            assertEquals(listOf(2L), awaitItem().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remove then restore puts the Header at position 0 and shifts the others`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        repository.removeHeader()
        assertEquals(listOf(10L), repository.places.first().flatMap { place -> place.widgets.map { widget -> widget.id } })
        repository.restoreHeader(position = 0)
        val restoredPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(2, restoredPlaces.size)
        assertEquals(PlacedWidget.Header::class, restoredPlaces.first().widgets.single()::class)
        assertEquals(listOf(0, 1), dao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
    }

    @Test
    fun `removeHeader returns the stored place position`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 5)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        assertEquals(5, repository.removeHeader() ?: -1)
    }

    @Test
    fun `resolves and deletes duplicate Headers`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                header(id = 11L, placeId = 1L, pagePosition = 1),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        val resolvedPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L), resolvedPlaces.map { place -> place.id })
        assertEquals(listOf(10L), resolvedPlaces.flatMap { place -> place.widgets.map { widget -> widget.id } })
        assertEquals(listOf(10L), dao.observeWidgets().first().map { widget -> widget.id })
        assertEquals(listOf(1L), dao.observePlaces().first().map { place -> place.id })
    }

    @Test
    fun `drops widgets with unknown kinds`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(
                WidgetRecord(
                    id = 1L,
                    placeId = 1L,
                    pagePosition = 0,
                    kind = WidgetKind.UNKNOWN
                )
            )
        )
        IWidgetAreaRepositoryImpl(dao).places.test {
            assertEquals(emptyList<WidgetPlace>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAppWidget inserts a place below the given place and shifts later positions`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        val provider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "Widget")
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(42),
                provider = provider,
                profileSerial = 1L,
                insertAt = InsertPosition.Below(placeId = 1L)
            )
        )
        val places: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(0, 1, 2), dao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
        val inserted: PlacedWidget.AppWidget = places[1].widgets.single() as PlacedWidget.AppWidget
        assertEquals(42, inserted.appWidgetId)
        assertEquals(provider, inserted.provider)
        assertEquals(1L, inserted.profileSerial)
    }

    @Test
    fun `addAppWidget adds at the end for End`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End
            )
        )
        val places: List<WidgetPlace> = repository.places.first()
        assertEquals(7, (places.last().widgets.single() as PlacedWidget.AppWidget).appWidgetId)
    }

    @Test
    fun `removeWidget deletes the place when empty and returns the system id`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 11L, placeId = 1L))
        )
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(dao)
        assertEquals(AppWidgetId(42), repository.removeWidget(widgetId = 11L))
        assertEquals(emptyList<WidgetPlace>(), repository.places.first())
        assertEquals(emptyList<WidgetPlaceRecord>(), dao.observePlaces().first())
        assertEquals(emptyList<WidgetRecord>(), dao.observeWidgets().first())
    }

    private fun place(id: Long, position: Int): WidgetPlaceRecord = WidgetPlaceRecord(
        id = id,
        position = position,
        heightDp = null,
        currentPage = 0
    )

    private fun header(id: Long, placeId: Long, pagePosition: Int): WidgetRecord = WidgetRecord(
        id = id,
        placeId = placeId,
        pagePosition = pagePosition,
        kind = WidgetKind.HEADER
    )

    private fun appWidget(id: Long, placeId: Long, pagePosition: Int = 0): WidgetRecord = WidgetRecord(
        id = id,
        placeId = placeId,
        pagePosition = pagePosition,
        kind = WidgetKind.APP_WIDGET,
        appWidgetId = 42,
        provider = "com.example.widget/WidgetProvider",
        profileSerial = 1L
    )
}
