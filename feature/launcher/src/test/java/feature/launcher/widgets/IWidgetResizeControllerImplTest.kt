package feature.launcher.widgets

import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.appWidgetPlace
import core.data.testing.headerPlace
import core.data.testing.stackPlace
import core.data.widgets.AppWidgetId
import core.data.widgets.WidgetProviderInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetResizeControllerImplTest {
    @Test
    fun `startResize uses provider bounds without the provider list having been collected`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100, appWidgetId = 9))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.providerInfoById[AppWidgetId(9)] = appWidgetProviderInfo(
            minResizeHeightDp = 64,
            maxResizeHeightDp = 160,
            isVerticallyResizable = true
        )
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        assertTrue(controller.startResize(placeId = 2L))

        val state: ResizeState = controller.resizeState.value!!
        assertEquals(64, state.bounds.minDp)
        assertEquals(160, state.bounds.maxDp)
        assertEquals(100, state.draftHeightDp)
    }

    @Test
    fun `startResize clamps the draft to the provider bounds`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(
                minHeightDp = 64,
                minResizeHeightDp = 64,
                maxResizeHeightDp = 160,
                isVerticallyResizable = true
            )
        )
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)

        val state: ResizeState = controller.resizeState.value!!
        assertEquals(100, state.draftHeightDp)
        assertEquals(64, state.bounds.minDp)
        assertEquals(160, state.bounds.maxDp)

        controller.dragResize(deltaDp = 1_000f)
        assertEquals(160, controller.resizeState.value!!.draftHeightDp)
        controller.dragResize(deltaDp = -1_000f)
        assertEquals(64, controller.resizeState.value!!.draftHeightDp)
    }

    @Test
    fun `dragResize accumulates fractional drag and snaps to the 8dp grid`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 400)
        controller.startResize(placeId = 2L)

        controller.dragResize(deltaDp = 4f)
        assertEquals(104, controller.resizeState.value!!.draftHeightDp)
        controller.dragResize(deltaDp = 3f)
        assertEquals(104, controller.resizeState.value!!.draftHeightDp)
        controller.dragResize(deltaDp = 5f)
        assertEquals(112, controller.resizeState.value!!.draftHeightDp)

        controller.dragResize(deltaDp = 1_000f)
        assertEquals(400, controller.resizeState.value!!.draftHeightDp)

        controller.dragResize(deltaDp = -1_000f)
        assertEquals(48, controller.resizeState.value!!.draftHeightDp)
    }

    @Test
    fun `caps the draft at a smaller Home height`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 300))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 200)
        controller.startResize(placeId = 2L)

        assertEquals(200, controller.resizeState.value!!.draftHeightDp)
        controller.setHomeHeight(heightDp = 120)
        assertEquals(120, controller.resizeState.value!!.draftHeightDp)
    }

    @Test
    fun `finishResize persists the draft height and reports one size`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val controller: IWidgetResizeController = createController(
            repository = repository,
            host = host,
            homeEditState = homeEditState
        )
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)
        controller.dragResize(deltaDp = 8f)

        val finish: ResizeFinish = controller.finishResize()!!
        val report: WidgetSizeReport = controller.persistResize(finish = finish)!!

        assertEquals(listOf(2L to 112), repository.heightUpdates)
        assertEquals(112, repository.places.first().first { place -> place.id == 2L }.heightDp)
        assertEquals(WidgetSizeReport(listOf(AppWidgetId(9)), widthDp = 240, heightDp = 112), report)
        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
    }

    @Test
    fun `finishResize at the provider default stores null`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(minHeightDp = 64, minResizeHeightDp = 48, isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)
        val resizeState: ResizeState = controller.resizeState.value!!
        controller.dragResize(deltaDp = (resizeState.defaultHeightDp - resizeState.draftHeightDp).toFloat())
        assertEquals(64, controller.resizeState.value!!.draftHeightDp)

        val finish: ResizeFinish = controller.finishResize()!!
        val report: WidgetSizeReport = controller.persistResize(finish = finish)!!

        assertEquals(listOf(2L to null), repository.heightUpdates)
        assertNull(repository.places.first().first { place -> place.id == 2L }.heightDp)
        assertEquals(WidgetSizeReport(listOf(AppWidgetId(9)), widthDp = 240, heightDp = 64), report)
    }

    @Test
    fun `startResize is ignored while another Home edit mode is active`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val homeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(
            repository = repository,
            host = host,
            homeEditState = homeEditState
        )

        assertFalse(controller.startResize(placeId = 2L))

        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.Favorites, homeEditState.mode.value)
    }

    @Test
    fun `cancels Resize when its place disappears without persisting`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(
            repository = repository,
            host = host,
            homeEditState = homeEditState
        )
        controller.setHomeHeight(heightDp = 400)
        controller.startResize(placeId = 2L)
        controller.dragResize(deltaDp = 8f)

        repository.removeWidget(widgetId = 2L)
        controller.onPlacesChanged(places = repository.places.first())

        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
        assertTrue(repository.heightUpdates.isEmpty())
    }

    @Test
    fun `onStackGrown clamps the stored height and reports the page size`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(stackPlace().copy(heightDp = 300))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        val controller: IWidgetResizeController = createController(repository = repository, host = host)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        val report: WidgetSizeReport = controller.onStackGrown(placeId = 1L, headerHeightDp = 100)!!

        assertEquals(listOf(1L to 200), repository.heightUpdates)
        assertEquals(200, repository.places.first().first { place -> place.id == 1L }.heightDp)
        assertEquals(listOf(AppWidgetId(9)), report.appWidgetIds)
    }

    private fun hostWithProviderInfo(info: WidgetProviderInfo): FakeAppWidgetHostSource =
        FakeAppWidgetHostSource().apply { providerInfoById[AppWidgetId(9)] = info }

    private fun createController(
        repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(),
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        homeEditState: HomeEditState = HomeEditState()
    ): IWidgetResizeController = IWidgetResizeControllerImpl(
        repository = repository,
        appWidgetHost = host,
        settingsRepository = settingsRepository,
        homeEditState = homeEditState
    )
}
