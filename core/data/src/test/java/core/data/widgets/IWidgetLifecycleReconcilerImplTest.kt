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
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.boundIdsState.addAll(listOf(AppWidgetId(7), AppWidgetId(8)))
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.reconcileAtStartup(pendingIds = emptySet())

        assertEquals(listOf(AppWidgetId(7), AppWidgetId(8)), host.deletedIds)
        assertTrue(reconciler.isStartupReconciled.value)
    }

    @Test
    fun `keeps an id that belongs to a pending add flow`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.boundIdsState.add(AppWidgetId(7))
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(7)))

        assertTrue(host.deletedIds.isEmpty())
    }

    @Test
    fun `deletes stored rows whose system id is gone and emits them`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.removedIds.test {
            reconciler.reconcileAtStartup(pendingIds = emptySet())
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a stored row whose id belongs to a pending add flow`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(42)))

        assertEquals(1, dao.getAppWidgets().size)
    }

    @Test
    fun `removes a widget whose provider package is gone`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.defaultAvailability = WidgetAvailability.Gone
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.removedIds.test {
            reconciler.removeGoneProviders()
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(AppWidgetId(42)), host.deletedIds)
        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a widget that is only temporarily unavailable`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao(
            initialPlaces = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            initialWidgets = listOf(appWidget(appWidgetId = 42))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.defaultAvailability = WidgetAvailability.Updating
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.removeGoneProviders()

        assertEquals(1, dao.getAppWidgets().size)
        assertTrue(host.deletedIds.isEmpty())
    }

    @Test
    fun `reconciles at startup only once`() = runTest {
        val dao: FakeWidgetAreaDao = FakeWidgetAreaDao()
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = host)

        reconciler.reconcileAtStartup(pendingIds = emptySet())
        host.boundIdsState.add(AppWidgetId(9))
        reconciler.reconcileAtStartup(pendingIds = emptySet())

        assertTrue(host.deletedIds.isEmpty())
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
