package core.data.widgets

import app.cash.turbine.test
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeWidgetAreaDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetAreaRepositoryImplTest {
    @Test
    fun `orders places by position and widgets by page position`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
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
        repository(mockDao).places.test {
            val actualPlaces: List<WidgetPlace> = awaitItem()
            assertEquals(listOf(1L, 2L), actualPlaces.map { place -> place.id })
            assertEquals(listOf(12L, 11L), actualPlaces.first().widgets.map { widget -> widget.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops places without widgets`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(header(id = 2L, placeId = 2L, pagePosition = 0))
        )
        repository(mockDao).places.test {
            assertEquals(listOf(2L), awaitItem().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remove then restore puts the Header at position 0 and shifts the others`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        repository.removeHeader()
        assertEquals(listOf(10L), repository.places.first().flatMap { place -> place.widgets.map { widget -> widget.id } })
        repository.restoreHeader(position = 0)
        val actualRestoredPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(2, actualRestoredPlaces.size)
        assertEquals(PlacedWidget.Header::class, actualRestoredPlaces.first().widgets.single()::class)
        assertEquals(listOf(0, 1), mockDao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
    }

    @Test
    fun `removeHeader returns the stored place position`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 5)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                header(id = 20L, placeId = 2L, pagePosition = 0)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        assertEquals(5, repository.removeHeader()?.position)
    }

    @Test
    fun `restoreHeader inserts the Header into the saved place and shifts its pages`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 10L, placeId = 1L, pagePosition = 0, appWidgetId = 100))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

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
    fun `resolves and deletes duplicate Headers`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
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
        val repository: IWidgetAreaRepository = repository(mockDao)
        val actualResolvedPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L), actualResolvedPlaces.map { place -> place.id })
        assertEquals(listOf(10L), actualResolvedPlaces.flatMap { place -> place.widgets.map { widget -> widget.id } })
        assertEquals(listOf(10L), mockDao.observeWidgets().first().map { widget -> widget.id })
        assertEquals(listOf(1L), mockDao.observePlaces().first().map { place -> place.id })
    }

    @Test
    fun `drops widgets with unknown kinds`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
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
        repository(mockDao).places.test {
            assertEquals(emptyList<WidgetPlace>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAppWidget inserts a place below the given place and shifts later positions`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        val inputProvider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "Widget")
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(42),
                provider = inputProvider,
                profileSerial = 1L,
                insertAt = InsertPosition.Below(placeId = 1L),
                defaultHeightDp = 48
            )
        )
        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(0, 1, 2), mockDao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position })
        val actualInserted: PlacedWidget.AppWidget = actualPlaces[1].widgets.single() as PlacedWidget.AppWidget
        assertEquals(42, actualInserted.appWidgetId)
        assertEquals(inputProvider, actualInserted.provider)
        assertEquals(1L, actualInserted.profileSerial)
    }

    @Test
    fun `addAppWidget adds at the end for End`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 48
            )
        )
        val actualPlaces: List<WidgetPlace> = repository.places.first()
        assertEquals(7, (actualPlaces.last().widgets.single() as PlacedWidget.AppWidget).appWidgetId)
    }

    @Test
    fun `addAppWidget stores the default height on the placed widget`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 64
            )
        )
        val actualStored: WidgetRecord = mockDao.observeWidgets().first().first { widget -> widget.appWidgetId == 7 }
        assertEquals(64, actualStored.defaultHeightDp)
        val actualPlaced: PlacedWidget.AppWidget = repository.places.first().last().widgets.single() as PlacedWidget.AppWidget
        assertEquals(64, actualPlaced.defaultHeightDp)
    }

    @Test
    fun `removeWidget deletes the place when empty and returns the system id`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 11L, placeId = 1L))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        assertEquals(AppWidgetId(42), repository.removeWidget(widgetId = 11L))
        assertEquals(emptyList<WidgetPlace>(), repository.places.first())
        assertEquals(emptyList<WidgetPlaceRecord>(), mockDao.observePlaces().first())
        assertEquals(emptyList<WidgetRecord>(), mockDao.observeWidgets().first())
    }

    @Test
    fun `hides the Widget Area until startup reconciliation finishes`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 10L, placeId = 1L))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.boundIdsState.add(AppWidgetId(42))
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)
        val repository: IWidgetAreaRepository = IWidgetAreaRepositoryImpl(mockDao, mockHost, reconciler)
        repository.places.test {
            assertEquals(emptyList<WidgetPlace>(), awaitItem())
            reconciler.reconcileAtStartup(pendingIds = emptySet())
            assertEquals(listOf(1L), awaitItem().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-resolves availability when providers change`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 10L, placeId = 1L))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.availabilityResults[AppWidgetId(42)] = WidgetAvailability.Available
        val repository: IWidgetAreaRepository = repository(mockDao = mockDao, mockHost = mockHost)
        repository.places.test {
            assertEquals(WidgetAvailability.Available, model(awaitItem()).availability)
            mockHost.availabilityResults[AppWidgetId(42)] = WidgetAvailability.Unknown
            mockHost.emitProviderChange()
            assertEquals(WidgetAvailability.Unknown, model(awaitItem()).availability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPlaceHeight stores and clears the height`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(id = 11L, placeId = 1L))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        repository.setPlaceHeight(placeId = 1L, heightDp = 120)
        assertEquals(120, repository.places.first().single().heightDp)

        repository.setPlaceHeight(placeId = 1L, heightDp = null)
        assertNull(repository.places.first().single().heightDp)
    }

    @Test
    fun `commitEdit removes places, rewrites contiguous positions and returns freed ids`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1),
                place(id = 3L, position = 2)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42),
                appWidget(id = 30L, placeId = 3L, appWidgetId = 7)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        val actualFreedIds: List<AppWidgetId> = repository.commitEdit(
            edit = WidgetAreaEdit(
                places = listOf(PlaceDraft(placeId = 3L, widgetIds = listOf(30L)), PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))
            )
        )

        assertEquals(listOf(AppWidgetId(42)), actualFreedIds)
        assertEquals(listOf(3L, 1L), repository.places.first().map { place -> place.id })
        assertEquals(
            listOf(0, 1),
            mockDao.observePlaces().first().sortedBy { place -> place.position }.map { place -> place.position }
        )
        assertEquals(emptyList<WidgetRecord>(), mockDao.observeWidgets().first().filter { widget -> widget.placeId == 2L })
    }

    @Test
    fun `commitEdit ignores removed places in the order`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0), place(id = 2L, position = 1)),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0), appWidget(id = 20L, placeId = 2L))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        repository.commitEdit(edit = WidgetAreaEdit(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))))

        assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
        assertEquals(listOf(0), mockDao.observePlaces().first().map { place -> place.position })
    }

    @Test
    fun `commitEdit keeps positions contiguous when a place vanished before commit`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1),
                place(id = 3L, position = 2)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L, pagePosition = 0, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, pagePosition = 0, appWidgetId = 200),
                appWidget(id = 30L, placeId = 3L, pagePosition = 0, appWidgetId = 300)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)
        mockDao.deletePlaces(placeIds = listOf(2L))

        repository.commitEdit(
            edit = WidgetAreaEdit(
                places = listOf(PlaceDraft(placeId = 3L, widgetIds = listOf(30L)), PlaceDraft(placeId = 1L, widgetIds = listOf(10L)))
            )
        )

        assertEquals(listOf(3L, 1L), repository.places.first().map { place -> place.id })
        assertEquals(listOf(1, 0), mockDao.observePlaces().first().sortedBy { place -> place.id }.map { place -> place.position })
    }

    @Test
    fun `addAppWidget into a stack appends a page and makes it current`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(header(id = 10L, placeId = 1L, pagePosition = 0))
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        repository.addAppWidget(
            request = AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                profileSerial = 0L,
                insertAt = InsertPosition.IntoStack(placeId = 1L),
                defaultHeightDp = 48
            )
        )

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(2, actualPlace.widgets.size)
        assertEquals(1, actualPlace.currentPage)
        assertEquals(10L, actualPlace.widgets.first().id)
    }

    @Test
    fun `removeWidget dissolves a two-page stack and resets the current page`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0, heightDp = 200, currentPage = 1)),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 1L, pagePosition = 1, appWidgetId = 7)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        repository.removeWidget(widgetId = 20L)

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(1, actualPlace.widgets.size)
        assertEquals(0, actualPlace.currentPage)
        assertNull(actualPlace.heightDp)
    }

    @Test
    fun `commitEdit moves a widget between places`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0), place(id = 2L, position = 1)),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

        repository.commitEdit(
            edit = WidgetAreaEdit(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(10L, 20L))))
        )

        val actualPlace: WidgetPlace = repository.places.first().single()
        assertEquals(listOf(10L, 20L), actualPlace.widgets.map { widget -> widget.id })
        assertEquals(0, actualPlace.currentPage)
    }

    @Test
    fun `commitEdit creates a place for a split page`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(place(id = 1L, position = 0)),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 1L, pagePosition = 1, appWidgetId = 200)
            )
        )
        val repository: IWidgetAreaRepository = repository(mockDao)

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
    fun `replaceHeader moves a single widget into the Header place and deletes its old place`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)

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
        assertNull(mockDao.getPlace(placeId = 2L))
    }

    @Test
    fun `replaceHeader moves the widget into a stack page and keeps the stack height`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0).copy(heightDp = 200),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 11L, placeId = 1L, pagePosition = 1, appWidgetId = 41),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)

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
        assertNull(mockDao.getPlace(placeId = 2L))
    }

    @Test
    fun `undoReplaceHeader restores places, heights and page positions`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)
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
        assertEquals(listOf(1L, 2L), mockDao.getPlaceIds())
    }

    @Test
    fun `undoReplaceHeader restores only the Header after the widget is uninstalled`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0).copy(heightDp = 64),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)
        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!
        mockDao.deleteByAppWidgetIds(listOf(42))

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
    fun `setExtendsUnderRail only widens lone widgets`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1)
            ),
            initialWidgets = listOf(
                appWidget(id = 10L, placeId = 1L),
                appWidget(id = 20L, placeId = 2L, pagePosition = 0, appWidgetId = 43),
                appWidget(id = 21L, placeId = 2L, pagePosition = 1, appWidgetId = 44)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)

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
    fun `undoReplaceHeader shifts a place that took the old position`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(
                place(id = 1L, position = 0),
                place(id = 2L, position = 1).copy(heightDp = 120)
            ),
            initialWidgets = listOf(
                header(id = 10L, placeId = 1L, pagePosition = 0),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 42)
            )
        )
        val repository: IWidgetAreaRepositoryImpl = repository(mockDao)
        val actualUndo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = 20L)!!
        val inputAddedPlaceId: Long = mockDao.insertPlace(WidgetPlaceRecord(position = 1))

        repository.undoReplaceHeader(undo = actualUndo)

        assertEquals(1, mockDao.getPlace(placeId = 2L)?.position)
        assertEquals(2, mockDao.getPlace(placeId = inputAddedPlaceId)?.position)
        assertEquals(listOf(1L, 2L, inputAddedPlaceId), mockDao.getPlaceIds())
    }

    private suspend fun repository(
        mockDao: FakeWidgetAreaDao,
        mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
    ): IWidgetAreaRepositoryImpl {
        mockHost.boundIdsState.addAll(mockDao.allAppWidgetIds().map { id -> AppWidgetId(id) })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)
        reconciler.reconcileAtStartup(pendingIds = emptySet())
        return IWidgetAreaRepositoryImpl(mockDao, mockHost, reconciler)
    }

    private fun model(places: List<WidgetPlace>): PlacedWidget.AppWidget = places.single().widgets.single() as PlacedWidget.AppWidget

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
