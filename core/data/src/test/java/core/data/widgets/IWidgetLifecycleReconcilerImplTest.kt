package core.data.widgets

import app.cash.turbine.test
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeWidgetAreaDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetLifecycleReconcilerImplTest {
    @Test
    fun `frees system ids with no stored row`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.boundIdsState.addAll(listOf(AppWidgetId(7), AppWidgetId(8)))
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = emptySet())

        assertEquals(listOf(AppWidgetId(7), AppWidgetId(8)), mockHost.deletedIds)
        assertTrue(reconciler.isStartupReconciled.value)
    }

    @Test
    fun `keeps an id that belongs to a pending add flow`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.boundIdsState.add(AppWidgetId(7))
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(7)))

        assertTrue(mockHost.deletedIds.isEmpty())
    }

    @Test
    fun `deletes stored rows whose system id is gone and emits them`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.removedIds.test {
            reconciler.reconcileAtStartup(pendingIds = emptySet())
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(mockDao.getAppWidgets().isEmpty())
        assertTrue(mockDao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a stored row whose id belongs to a pending add flow`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(42)))

        assertEquals(1, mockDao.getAppWidgets().size)
    }

    @Test
    fun `removes a widget whose provider package is gone`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.defaultAvailability = WidgetAvailability.Gone
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.removedIds.test {
            reconciler.removeGoneProviders()
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(AppWidgetId(42)), mockHost.deletedIds)
        assertTrue(mockDao.getAppWidgets().isEmpty())
        assertTrue(mockDao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a widget that is only temporarily unavailable`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        mockHost.defaultAvailability = WidgetAvailability.Updating
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.removeGoneProviders()

        assertEquals(1, mockDao.getAppWidgets().size)
        assertTrue(mockHost.deletedIds.isEmpty())
    }

    @Test
    fun `reconciles at startup only once`() = runTest {
        val mockDao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = mockDao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = emptySet())
        mockHost.boundIdsState.add(AppWidgetId(9))
        reconciler.reconcileAtStartup(pendingIds = emptySet())

        assertTrue(mockHost.deletedIds.isEmpty())
        assertTrue(reconciler.isStartupReconciled.value)
    }

    private fun appWidget(appWidgetId: Int): WidgetRecord = WidgetRecord(
        placeId = 1L,
        pagePosition = 0,
        kind = WidgetKind.APP_WIDGET,
        appWidgetId = appWidgetId,
        provider = "com.example/ExampleWidget",
        profileSerial = 0L
    )
}
