package core.data.widgets

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.LaunchableReadiness
import core.data.launchables.LaunchableState
import core.data.room.dao.WidgetAreaDao
import core.data.testing.AppWidgetHostState
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.ProfileSourceState
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createLaunchableState
import core.data.testing.createProfileSourceMock
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HIDDEN_APP_KEY: String = "com.example.hidden"

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetLifecycleReconcilerImplTest : InMemoryDatabaseTest() {
    @Test
    fun `frees system ids with no stored row`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf(AppWidgetId(7), AppWidgetId(8))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
    fun `removes gone providers only once the live App List is ready`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            availabilityProvider = { WidgetAvailability.Gone }
        )
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), readiness = LaunchableReadiness.LastKnownHomeReady)
        )
        val collection = backgroundScope.launch {
            reconciler.removeGoneProvidersWhenAppListReady(
                launchableState = inputLaunchableState,
                providerChanges = MutableSharedFlow()
            )
        }
        try {
            finishPendingWork()

            assertEquals(1, dao.getAppWidgets().size)
            verify(exactly = 0) { mockHost.deleteId(any()) }

            inputLaunchableState.value =
                createLaunchableState(groups = emptyList(), readiness = LaunchableReadiness.AppListReady)
            finishPendingWork()

            assertTrue(dao.getAppWidgets().isEmpty())
            verify(exactly = 1) { mockHost.deleteId(id = AppWidgetId(42)) }
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun `scans for gone providers on provider changes, not on every Launchable State update`() = databaseTest {
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0)),
            widgets = listOf(appWidget(appWidgetId = 42))
        )
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = widgetAreaDao(), host = mockHost, profileSource = createProfileSourceMock())
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = emptyList()))
        val inputProviderChanges: MutableSharedFlow<Unit> = MutableSharedFlow()
        val collection = backgroundScope.launch {
            reconciler.removeGoneProvidersWhenAppListReady(
                launchableState = inputLaunchableState,
                providerChanges = inputProviderChanges
            )
        }
        try {
            finishPendingWork()
            verify(exactly = 1) { mockHost.availability(any(), any(), any()) }

            inputLaunchableState.value = createLaunchableState(groups = emptyList(), hiddenKeys = setOf(HIDDEN_APP_KEY))
            finishPendingWork()
            verify(exactly = 1) { mockHost.availability(any(), any(), any()) }

            inputProviderChanges.emit(Unit)
            finishPendingWork()
            verify(exactly = 2) { mockHost.availability(any(), any(), any()) }
        } finally {
            collection.cancel()
        }
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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

        reconciler.removeGoneProviders()

        verify(exactly = 1) { mockHost.deleteId(id = AppWidgetId(42)) }
        assertTrue(dao.getAppWidgets().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `waits for the Profiles before reconciling the stored layout`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        seedWidgetArea(
            places = listOf(WidgetPlaceRecord(id = 1L, position = 0, heightDp = 20)),
            widgets = listOf(appWidget(appWidgetId = 42), appWidget(appWidgetId = 43))
        )
        val inputProfileState: ProfileSourceState = ProfileSourceState(isLoaded = false)
        val inputHostState: AppWidgetHostState = AppWidgetHostState().apply {
            providerInfoById[AppWidgetId(43)] = providerInfo(minHeightDp = 300)
        }
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(
            state = inputHostState,
            boundIdsProvider = { setOf(AppWidgetId(43)) }
        )
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(
            dao = dao,
            host = mockHost,
            profileSource = createProfileSourceMock(state = inputProfileState)
        )
        val startupReconciliation: Deferred<Unit> = async { reconciler.reconcileAtStartup(pendingIds = emptySet()) }
        runCurrent()

        assertFalse(reconciler.isStartupReconciled.value)
        assertEquals(20, dao.getPlace(placeId = 1L)?.heightDp)
        assertEquals(2, dao.getAppWidgets().size)
        verify(exactly = 0) { mockHost.providerInfo(AppWidgetId(43)) }

        inputProfileState.isLoaded.value = true
        runCurrent()
        startupReconciliation.await()

        assertTrue(reconciler.isStartupReconciled.value)
        assertEquals(300, dao.getPlace(placeId = 1L)?.heightDp)
        assertEquals(1, dao.getAppWidgets().size)
    }

    @Test
    fun `reconciles at startup only once`() = databaseTest {
        val dao: WidgetAreaDao = widgetAreaDao()
        val boundIds: MutableSet<AppWidgetId> = mutableSetOf()
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(boundIdsProvider = { boundIds })
        val reconciler: IWidgetLifecycleReconcilerImpl =
            IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost, profileSource = createProfileSourceMock())

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

    private fun providerInfo(minHeightDp: Int): WidgetProviderInfo = WidgetProviderInfo(
        provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
        profileSerial = 0L,
        appPackageName = "com.example",
        appLabel = "Example",
        appIcon = Icon.RailIcon(resId = 1),
        label = "Widget",
        description = null,
        defaultSize = WidgetSize(widthDp = 100, heightDp = 80),
        cellSize = null,
        minHeightDp = minHeightDp,
        minResizeHeightDp = 0,
        maxResizeHeightDp = null,
        needsConfiguration = false,
        isReconfigurable = false,
        hasConfigureActivity = false,
        isWorkProfile = false
    )
}
