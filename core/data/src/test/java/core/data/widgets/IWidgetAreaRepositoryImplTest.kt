package core.data.widgets

import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.room.dao.WidgetAreaDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createApp
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createLaunchableRepositoryMock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetAreaRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `orders places by position and widgets by page position`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 2L, position = 1),
                place(id = 1L, position = 0)
            ),
            widgets = listOf(
                appWidget(id = 22L, placeId = 2L),
                header(id = 12L, placeId = 1L, pagePosition = 0),
                appWidget(id = 11L, placeId = 1L, pagePosition = 1)
            )
        )
        repository(dao).places.test {
            val actualPlaces: List<WidgetPlace> = awaitItem()
            assertEquals(listOf(1L, 2L), actualPlaces.map { place -> place.id })
            assertEquals(listOf(12L, 11L), actualPlaces.first().widgets.map { widget -> widget.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops places without widgets`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            widgets = listOf(header(id = 2L, placeId = 2L, pagePosition = 0))
        )
        repository(dao).places.test {
            assertEquals(listOf(2L), awaitItem().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remove then restore puts the Header at position 0 and shifts the others`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)
        repository.removeHeader()
        assertEquals(listOf(10L), repository.places.first().flatMap { place -> place.widgets.map { widget -> widget.id } })
        repository.restoreHeader(position = 0)
        val actualRestoredPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(2, actualRestoredPlaces.size)
        assertEquals(PlacedWidget.Header::class, actualRestoredPlaces.first().widgets.single()::class)
        assertEquals(listOf(0, 1), dao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
    }

    @Test
    fun `removeHeader returns the stored place position`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 5)
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)
        assertEquals(5, repository.removeHeader()?.position)
    }

    @Test
    fun `restoreHeader inserts the Header into the saved place and shifts its pages`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(appWidget(id = 10L, placeId = 1L, pagePosition = 0, appWidgetId = 100))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.restoreHeader(position = 0, placeId = 1L, pagePosition = 0)

        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L), actualPlaces.map { place -> place.id })
        val actualPlace: WidgetPlace = actualPlaces.single()
        assertEquals(2, actualPlace.widgets.size)
        assertTrue(actualPlace.widgets.first() is PlacedWidget.Header)
        assertEquals(10L, actualPlace.widgets.last().id)
        assertEquals(0, actualPlace.currentPage)
    }

    @Test
    fun `resolves and deletes duplicate Headers`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                header(id = 11L, placeId = 1L, pagePosition = 1),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)
        val actualResolvedPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L), actualResolvedPlaces.map { place -> place.id })
        assertEquals(listOf(10L), actualResolvedPlaces.flatMap { place -> place.widgets.map { widget -> widget.id } })
        assertEquals(listOf(10L), dao.observeWidgets().first().map { widget -> widget.id })
        assertEquals(listOf(1L), dao.observePlaces().first().map { place -> place.id })
    }

    @Test
    fun `drops widgets with unknown kinds`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(
                WidgetRecord(
                    id = 1L,
                    placeId = 1L,
                    pagePosition = 0,
                    kind = WidgetKind.UNKNOWN
                )
            )
        )
        repository(dao).places.test {
            assertEquals(emptyList<WidgetPlace>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAppWidget inserts a place below the given place and shifts later positions`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            widgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(dao)
        val inputProvider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "Widget")
        val actualAdded: AddedAppWidget = repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(42),
                provider = inputProvider,
                profileSerial = 1L,
                insertAt = InsertPosition.Below(placeId = 1L),
                defaultHeightDp = 48
            )
        )
        assertEquals(AddedAppWidget(widgetId = 11L, placeId = 3L), actualAdded)
        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(0, 1, 2), dao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
        val actualInserted: PlacedWidget.AppWidget = actualPlaces[1].widgets.single() as PlacedWidget.AppWidget
        assertEquals(42, actualInserted.appWidgetId)
        assertEquals(inputProvider, actualInserted.provider)
        assertEquals(1L, actualInserted.profileSerial)
    }

    @Test
    fun `addAppWidget adds at the end for End`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(dao)
        val actualAdded: AddedAppWidget = repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 48
            )
        )
        assertEquals(AddedAppWidget(widgetId = 11L, placeId = 2L), actualAdded)
        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(7, (actualPlaces.last().widgets.single() as PlacedWidget.AppWidget).appWidgetId)
    }

    @Test
    fun `addAppWidget stores the default height on the placed widget`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(dao)
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 64
            )
        )
        val actualStored: WidgetRecord = dao.observeWidgets().first().first { widget -> widget.appWidgetId == 7 }
        assertEquals(64, actualStored.defaultHeightDp)
        val actualPlaced: PlacedWidget.AppWidget = repository.places.first().last().widgets.single() as PlacedWidget.AppWidget
        assertEquals(64, actualPlaced.defaultHeightDp)
    }

    @Test
    fun `removeWidget deletes the place when empty and returns the system id`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(appWidget(id = 11L, placeId = 1L))
        )
        val repository: IWidgetAreaRepository = repository(dao)
        assertEquals(AppWidgetId(42), repository.removeWidget(widgetId = 11L))
        assertEquals(emptyList<WidgetPlace>(), repository.places.first())
        assertEquals(emptyList<WidgetPlaceRecord>(), dao.observePlaces().first())
        assertEquals(emptyList<WidgetRecord>(), dao.observeWidgets().first())
    }

    @Test
    fun `hides the Widget Area until startup reconciliation finishes`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(appWidget(id = 10L, placeId = 1L))
        )
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf(AppWidgetId(42))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(
            widgetAreaDao = dao,
            appWidgetHost = mockHost,
            lifecycleReconciler = reconciler,
            launchableRepository = createLaunchableRepositoryMock()
        )
        repository.places.test {
            assertEquals(emptyList<WidgetPlace>(), awaitItem())
            reconciler.reconcileAtStartup(pendingIds = emptySet())
            assertEquals(listOf(1L), awaitItem().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-resolves availability when providers change`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(appWidget(id = 10L, placeId = 1L))
        )
        val availability: MutableMap<AppWidgetId, WidgetAvailability> = mutableMapOf(
            AppWidgetId(42) to WidgetAvailability.Available
        )
        val providerChanges: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
        val repository: IWidgetAreaRepository = repository(
            dao = dao,
            availabilityProvider = { id -> availability[id] ?: WidgetAvailability.Available },
            providerChangesFlow = providerChanges
        )
        repository.places.test {
            assertEquals(WidgetAvailability.Available, model(awaitItem()).availability)
            availability[AppWidgetId(42)] = WidgetAvailability.Unknown
            providerChanges.tryEmit(Unit)
            assertEquals(WidgetAvailability.Unknown, model(awaitItem()).availability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPlaceHeight stores and clears the height`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(appWidget(id = 11L, placeId = 1L))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.setPlaceHeight(placeId = 1L, heightDp = 120)
        assertEquals(120, repository.places.first().single().heightDp)

        repository.setPlaceHeight(placeId = 1L, heightDp = null)
        assertNull(repository.places.first().single().heightDp)
    }

    @Test
    fun `commitEdit removes places, rewrites contiguous positions and returns freed ids`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1),
                place(id = 3L, position = 2)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42),
                appWidget(id = 30L, placeId = 3L, appWidgetId = 7)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        val actualFreedIds: List<AppWidgetId> = repository.commitEdit(
            edit = WidgetAreaEdit(
                places = listOf(PlaceDraft(placeId = 3L, widgetIds = listOf(30L)), PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))
            )
        )

        assertEquals(listOf(AppWidgetId(42)), actualFreedIds)
        assertEquals(listOf(3L, 1L), repository.places.first().map { place -> place.id })
        assertEquals(
            listOf(0, 1),
            dao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position }
        )
        assertEquals(emptyList<WidgetRecord>(), dao.observeWidgets().first().filter { widget -> widget.placeId == 2L })
    }

    @Test
    fun `commitEdit keeps Pop-up places`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                popupPlace(id = 2L, ownerKey = "owner-key")
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        val actualFreedIds: List<AppWidgetId> = repository.commitEdit(
            edit = WidgetAreaEdit(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(10L))))
        )

        assertTrue(actualFreedIds.isEmpty())
        assertEquals(1, repository.popupPlace(ownerKey = "owner-key").first()?.widgets?.size)
    }

    @Test
    fun `commitEdit ignores removed places in the order`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0), place(id = 2L, position = 1)),
            widgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0), appWidget(id = 20L, placeId = 2L))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.commitEdit(edit = WidgetAreaEdit(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))))

        assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
        assertEquals(listOf(0), dao.observePlaces().first().map { place -> place.position })
    }

    @Test
    fun `commitEdit keeps positions contiguous when a place vanished before commit`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1),
                place(id = 3L, position = 2)
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, pagePosition = 0, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, pagePosition = 0, appWidgetId = 200),
                appWidget(id = 30L, placeId = 3L, pagePosition = 0, appWidgetId = 300)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)
        dao.deletePlaces(placeIds = listOf(2L))

        repository.commitEdit(
            edit = WidgetAreaEdit(
                places = listOf(PlaceDraft(placeId = 3L, widgetIds = listOf(30L)), PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))
            )
        )

        assertEquals(listOf(3L, 1L), repository.places.first().map { place -> place.id })
        assertEquals(listOf(1, 0), dao.observePlaces().first().sortedBy { place -> place.id }.map { place -> place.position })
    }

    @Test
    fun `addAppWidget into a stack appends a page and makes it current`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        val actualAdded: AddedAppWidget = repository.addAppWidget(
            request = AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                profileSerial = 0L,
                insertAt = InsertPosition.IntoStack(placeId = 1L),
                defaultHeightDp = 48
            )
        )

        assertEquals(AddedAppWidget(widgetId = 11L, placeId = 1L), actualAdded)
        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(2, actualPlace.widgets.size)
        assertEquals(1, actualPlace.currentPage)
        assertEquals(10L, actualPlace.widgets.first().id)
    }

    @Test
    fun `removeWidget dissolves a two-page stack and resets the current page`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0, heightDp = 200, currentPage = 1)),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 1L, pagePosition = 1, appWidgetId = 7)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.removeWidget(widgetId = 20L)

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(1, actualPlace.widgets.size)
        assertEquals(0, actualPlace.currentPage)
        assertNull(actualPlace.heightDp)
    }

    @Test
    fun `commitEdit moves a widget between places`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0), place(id = 2L, position = 1)),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.commitEdit(
            edit = WidgetAreaEdit(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(10L, 20L))))
        )

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(listOf(10L, 20L), actualPlace.widgets.map { widget -> widget.id })
        assertEquals(0, actualPlace.currentPage)
    }

    @Test
    fun `commitEdit creates a place for a split page`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(place(id = 1L, position = 0)),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 1L, pagePosition = 1, appWidgetId = 200)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.commitEdit(
            edit = WidgetAreaEdit(
                places = listOf(
                    PlaceDraft(placeId = 1L, widgetIds = listOf(10L)),
                    PlaceDraft(placeId = null, widgetIds = listOf(20L))
                )
            )
        )

        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(2, actualPlaces.size)
        assertEquals(listOf(10L, 20L), actualPlaces.flatMap { place -> place.widgets }.map { widget -> widget.id })
    }

    @Test
    fun `replaceHeader moves a single widget into the Header place and deletes its old place`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)

        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!

        assertEquals(1L, actualUndo.headerPlaceId)
        assertEquals(0, actualUndo.headerPosition)
        assertEquals(64, actualUndo.headerHeightDp)
        assertEquals(2L, actualUndo.widgetPlaceId)
        assertEquals(1, actualUndo.widgetPosition)
        assertEquals(120, actualUndo.widgetHeightDp)
        repository.places.test {
            val actualPlaces: List<WidgetPlace> = awaitItem()
            assertEquals(listOf(1L), actualPlaces.map { place -> place.id })
            assertEquals(120, actualPlaces.single().heightDp)
            assertEquals(listOf(20L), actualPlaces.single().widgets.map { widget -> widget.id })
            assertEquals(42, (actualPlaces.single().widgets.single() as PlacedWidget.AppWidget).appWidgetId)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(dao.getPlace(placeId = 2L))
    }

    @Test
    fun `replaceHeader moves the widget into a stack page and keeps the stack height`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0).copy(heightDp = 200),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 11L, placeId = 1L, pagePosition = 1, appWidgetId = 41),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)

        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!

        assertEquals(200, actualUndo.headerHeightDp)
        repository.places.test {
            val actualPlace: WidgetPlace = awaitItem().single()
            assertEquals(1L, actualPlace.id)
            assertEquals(200, actualPlace.heightDp)
            assertEquals(listOf(20L, 11L), actualPlace.widgets.map { widget -> widget.id })
            assertEquals(0, actualPlace.currentPage)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(dao.getPlace(placeId = 2L))
    }

    @Test
    fun `undoReplaceHeader restores places, heights and page positions`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)
        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!

        repository.undoReplaceHeader(undo = actualUndo)

        repository.places.test {
            val actualPlaces: List<WidgetPlace> = awaitItem()
            assertEquals(listOf(1L, 2L), actualPlaces.map { place -> place.id })
            assertEquals(listOf(64, 120), actualPlaces.map { place -> place.heightDp })
            assertTrue(actualPlaces.first().widgets.single() is PlacedWidget.Header)
            assertEquals(20L, actualPlaces.last().widgets.single().id)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(1L, 2L), dao.getPlaceIds())
    }

    @Test
    fun `undoReplaceHeader restores only the Header after the widget is uninstalled`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)
        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!
        dao.deleteByAppWidgetIds(listOf(42))

        repository.undoReplaceHeader(undo = actualUndo)

        repository.places.test {
            val actualPlace: WidgetPlace = awaitItem().single()
            assertEquals(1L, actualPlace.id)
            assertEquals(64, actualPlace.heightDp)
            assertTrue(actualPlace.widgets.single() is PlacedWidget.Header)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setExtendsUnderRail only widens lone widgets`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                appWidget(id = 20L, placeId = 2L, pagePosition = 0, appWidgetId = 43),
                appWidget(id = 21L, placeId = 2L, pagePosition = 1, appWidgetId = 44)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)

        repository.setExtendsUnderRail(widgetId = 10L, extendsUnderRail = true)
        repository.setExtendsUnderRail(widgetId = 20L, extendsUnderRail = true)

        repository.places.test {
            val actualPlaces: List<WidgetPlace> = awaitItem()
            assertEquals(WidgetWidth.UnderRail, actualPlaces.first().widthMode)
            assertEquals(WidgetWidth.Inset, actualPlaces.last().widthMode)
            assertEquals(true, (actualPlaces.first().widgets.single() as PlacedWidget.AppWidget).extendsUnderRail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undoReplaceHeader shifts a place that took the old position`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            widgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(dao)
        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!
        val inputAddedPlaceId: Long = dao.insertPlace(WidgetPlaceRecord(position = 1))

        repository.undoReplaceHeader(undo = actualUndo)

        assertEquals(1, dao.getPlace(placeId = 2L)?.position)
        assertEquals(2, dao.getPlace(placeId = inputAddedPlaceId)?.position)
        assertEquals(listOf(1L, 2L, inputAddedPlaceId), dao.getPlaceIds())
    }

    @Test
    fun `excludes Pop-up places from the Widget Area`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                popupPlace(id = 2L, ownerKey = "owner-key")
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )

        val actualPlaces: List<WidgetPlace> = repository(dao).places.first()

        assertEquals(listOf(1L), actualPlaces.map { place -> place.id })
        assertEquals(null, actualPlaces.single().ownerKey)
    }

    @Test
    fun `popupPlace returns the place owned by its Pop-up`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(
                place(id = 1L, position = 0),
                popupPlace(id = 2L, ownerKey = "owner-key")
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )

        val actualPlace: WidgetPlace? = repository(dao).popupPlace(ownerKey = "owner-key").first()

        assertEquals(2L, actualPlace?.id)
        assertEquals("owner-key", actualPlace?.ownerKey)
        assertEquals(200, (actualPlace?.widgets?.single() as PlacedWidget.AppWidget).appWidgetId)
    }

    @Test
    fun `popupPlace resolves a place stored under a Legacy App key`() = databaseTest {
        val inputApp: App = createApp(packageName = "com.example.app")
        val inputLegacyKey: String = "${inputApp.packageName}_${inputApp.profile}"
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(popupPlace(id = 2L, ownerKey = inputLegacyKey)),
            widgets = listOf(appWidget(id = 20L, placeId = 2L, appWidgetId = 200))
        )

        val actualPlace: WidgetPlace? = repository(
            dao = dao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputApp))
        ).popupPlace(ownerKey = inputApp.key).first()

        assertEquals(2L, actualPlace?.id)
        assertEquals(200, (actualPlace?.widgets?.single() as PlacedWidget.AppWidget).appWidgetId)
    }

    @Test
    fun `removeOwnerPlaces removes a place stored under a Legacy App key`() = databaseTest {
        val inputApp: App = createApp(packageName = "com.example.app")
        val inputLegacyKey: String = "${inputApp.packageName}_${inputApp.profile}"
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(popupPlace(id = 2L, ownerKey = inputLegacyKey)),
            widgets = listOf(appWidget(id = 20L, placeId = 2L, appWidgetId = 200))
        )

        val actualFreedIds: List<AppWidgetId> = repository(
            dao = dao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputApp))
        ).removeOwnerPlaces(ownerKey = inputApp.key)

        assertEquals(listOf(AppWidgetId(200)), actualFreedIds)
        assertTrue(dao.observePopupOwnerKeys().first().isEmpty())
    }

    @Test
    fun `addAppWidget with an owner key creates and fills its Pop-up place`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea()
        val repository: IWidgetAreaRepository = repository(dao)

        val actualAdded: AddedAppWidget = repository.addAppWidget(
            request = AddAppWidget(
                appWidgetId = AppWidgetId(77),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 48,
                ownerKey = "owner-key"
            )
        )

        val actualPlace: WidgetPlace = repository.popupPlace(ownerKey = "owner-key").first()!!
        assertEquals(actualAdded.placeId, actualPlace.id)
        assertEquals("owner-key", actualPlace.ownerKey)
        assertEquals(77, (actualPlace.widgets.single() as PlacedWidget.AppWidget).appWidgetId)
        assertTrue(repository.places.first().isEmpty())
    }

    @Test
    fun `addAppWidget with an owner key appends a page and makes it current`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(popupPlace(id = 2L, ownerKey = "owner-key")),
            widgets = listOf(appWidget(id = 20L, placeId = 2L, appWidgetId = 200))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        val actualAdded: AddedAppWidget = repository.addAppWidget(
            request = AddAppWidget(
                appWidgetId = AppWidgetId(201),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.IntoStack(placeId = 2L),
                defaultHeightDp = 48,
                ownerKey = "owner-key"
            )
        )

        val actualPlace: WidgetPlace = repository.popupPlace(ownerKey = "owner-key").first()!!
        assertEquals(actualAdded.placeId, actualPlace.id)
        assertEquals(listOf(20L, 21L), actualPlace.widgets.map { widget -> widget.id })
        assertEquals(1, actualPlace.currentPage)
        assertEquals(1, dao.getPlace(placeId = 2L)?.currentPage)
    }

    @Test
    fun `setCurrentPage stores the page of an owned place`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(popupPlace(id = 2L, ownerKey = "owner-key")),
            widgets = listOf(
                appWidget(id = 20L, placeId = 2L, pagePosition = 0, appWidgetId = 200),
                appWidget(id = 21L, placeId = 2L, pagePosition = 1, appWidgetId = 201)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.setCurrentPage(placeId = 2L, page = 1)

        assertEquals(1, repository.popupPlace(ownerKey = "owner-key").first()?.currentPage)
        assertEquals(1, dao.getPlace(placeId = 2L)?.currentPage)
    }

    @Test
    fun `removeWidget clamps the current page of a remaining stack`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0, currentPage = 2)),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, pagePosition = 0, appWidgetId = 100),
                appWidget(id = 20L, placeId = 1L, pagePosition = 1, appWidgetId = 200),
                appWidget(id = 30L, placeId = 1L, pagePosition = 2, appWidgetId = 300)
            )
        )
        val repository: IWidgetAreaRepository = repository(dao)

        repository.removeWidget(widgetId = 30L)

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(1, actualPlace.currentPage)
        assertEquals(1, dao.getPlace(placeId = 1L)?.currentPage)
    }

    @Test
    fun `removeOwnerPlaces deletes its place and returns the freed ids`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(popupPlace(id = 2L, ownerKey = "owner-key")),
            widgets = listOf(appWidget(id = 20L, placeId = 2L, appWidgetId = 200))
        )
        val repository: IWidgetAreaRepository = repository(dao)

        val actualFreedIds: List<AppWidgetId> = repository.removeOwnerPlaces(ownerKey = "owner-key")

        assertEquals(listOf(AppWidgetId(200)), actualFreedIds)
        assertTrue(dao.getPlaceIds().isEmpty())
        assertTrue(dao.getAppWidgets().isEmpty())
    }

    private suspend fun repository(
        dao: WidgetAreaDao,
        availabilityProvider: (AppWidgetId) -> WidgetAvailability = { WidgetAvailability.Available },
        providerChangesFlow: Flow<Unit> = emptyFlow(),
        mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock()
    ): IWidgetAreaRepositoryImpl {
        val boundIds: Set<AppWidgetId> = dao.boundAppWidgetIds()
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            boundIdsProvider = { boundIds },
            availabilityProvider = availabilityProvider,
            providerChangesFlow = providerChangesFlow
        )
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)
        reconciler.reconcileAtStartup(pendingIds = emptySet())
        return IWidgetAreaRepositoryImpl(
            widgetAreaDao = dao,
            appWidgetHost = mockHost,
            lifecycleReconciler = reconciler,
            launchableRepository = mockLaunchableRepository
        )
    }

    private suspend fun WidgetAreaDao.boundAppWidgetIds(): Set<AppWidgetId> =
        allAppWidgetIds().mapTo(mutableSetOf()) { id -> AppWidgetId(id) }

    private fun model(places: List<WidgetPlace>): PlacedWidget.AppWidget = places.single().widgets.single() as PlacedWidget.AppWidget

    private fun place(id: Long, position: Int): WidgetPlaceRecord = WidgetPlaceRecord(
        id = id,
        position = position,
        heightDp = null,
        currentPage = 0
    )

    private fun popupPlace(id: Long, ownerKey: String): WidgetPlaceRecord = WidgetPlaceRecord(
        id = id,
        position = 0,
        heightDp = null,
        currentPage = 0,
        ownerKey = ownerKey
    )

    private fun header(id: Long, placeId: Long, pagePosition: Int): WidgetRecord = WidgetRecord(
        id = id,
        placeId = placeId,
        pagePosition = pagePosition,
        kind = WidgetKind.HEADER
    )

    private fun appWidget(id: Long, placeId: Long, pagePosition: Int = 0, appWidgetId: Int = 42): WidgetRecord = WidgetRecord(
        id = id,
        placeId = placeId,
        pagePosition = pagePosition,
        kind = WidgetKind.APP_WIDGET,
        appWidgetId = appWidgetId,
        provider = "com.example.widget/WidgetProvider",
        profileSerial = 1L
    )
}
