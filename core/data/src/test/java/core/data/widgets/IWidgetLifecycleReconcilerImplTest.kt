package core.data.widgets

import app.cash.turbine.test
import core.data.room.dao.WidgetAreaDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createAppWidgetHostMock
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetLifecycleReconcilerImplTest : InMemoryDatabaseTest() {
    @Test
    fun `frees system ids with no stored row`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf(AppWidgetId(7), AppWidgetId(8))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = emptySet())

        verifyOrder {
            mockHost.deleteId(id = AppWidgetId(7))
            mockHost.deleteId(id = AppWidgetId(8))
        }
        verify(exactly = 2) { mockHost.deleteId(any()) }
        assertTrue(reconciler.isStartupReconciled.value)
    }

    @Test
    fun `keeps an id that belongs to a pending add flow`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf(AppWidgetId(7))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(7)))

        verify(exactly = 0) { mockHost.deleteId(any()) }
    }

    @Test
    fun `deletes stored rows whose system id is gone and emits them`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.removedIds.test {
            reconciler.reconcileAtStartup(pendingIds = emptySet())
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a stored row whose id belongs to a pending add flow`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = setOf(AppWidgetId(42)))

        assertEquals(1, dao.getAppWidgets().size)
    }

    @Test
    fun `removes a widget whose provider package is gone`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            availabilityProvider = { WidgetAvailability.Gone }
        )
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.removedIds.test {
            reconciler.removeGoneProviders()
            assertEquals(AppWidgetId(42), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { mockHost.deleteId(id = AppWidgetId(42)) }
        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `keeps a widget that is only temporarily unavailable`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            availabilityProvider = { WidgetAvailability.Updating }
        )
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.removeGoneProviders()

        assertEquals(1, dao.getAppWidgets().size)
        verify(exactly = 0) { mockHost.deleteId(any()) }
    }

    @Test
    fun `keeps a Pop-up widget at startup while its system id is bound`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0, ownerKey = "owner-key")),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf(AppWidgetId(42))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = emptySet())

        assertEquals(1, dao.getAppWidgets().size)
        verify(exactly = 0) { mockHost.deleteId(any()) }
    }

    @Test
    fun `removes a Pop-up widget whose provider is gone`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0, ownerKey = "owner-key")),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            availabilityProvider = { WidgetAvailability.Gone }
        )
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.removeGoneProviders()

        verify(exactly = 1) { mockHost.deleteId(id = AppWidgetId(42)) }
        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `reconciles at startup only once`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf()
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)

        reconciler.reconcileAtStartup(pendingIds = emptySet())
        boundIds += AppWidgetId(9)
        reconciler.reconcileAtStartup(pendingIds = emptySet())

        verify(exactly = 0) { mockHost.deleteId(any()) }
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
