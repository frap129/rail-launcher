package feature.launcher.widgets

import core.data.testing.appWidgetPlace
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.testing.headerPlace
import core.data.testing.popupStackPlace
import core.data.testing.stackPlace
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.WidgetPlace
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IWidgetResizeControllerImplTest {
    @Test
    fun `startResize uses provider bounds without the provider list having been collected`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100, appWidgetId = 9))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(
                minResizeHeightDp = 64,
                maxResizeHeightDp = 160,
                isVerticallyResizable = true
            )
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        assertTrue(controller.startResize(placeId = 2L))

        val actualState: ResizeState = controller.resizeState.value!!
        assertEquals(64, actualState.bounds.minDp)
        assertEquals(160, actualState.bounds.maxDp)
        assertEquals(100, actualState.draftHeightDp)
    }

    @Test
    fun `startResize clamps the draft to the provider bounds`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(
                minHeightDp = 64,
                minResizeHeightDp = 64,
                maxResizeHeightDp = 160,
                isVerticallyResizable = true
            )
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)

        val actualState: ResizeState = controller.resizeState.value!!
        assertEquals(100, actualState.draftHeightDp)
        assertEquals(64, actualState.bounds.minDp)
        assertEquals(160, actualState.bounds.maxDp)

        controller.dragResize(deltaDp = 1_000f)
        assertEquals(160, controller.resizeState.value!!.draftHeightDp)
        controller.dragResize(deltaDp = -1_000f)
        assertEquals(64, controller.resizeState.value!!.draftHeightDp)
    }

    @Test
    fun `dragResize accumulates fractional drag and snaps to the 8dp grid`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
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
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 300))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 200)
        controller.startResize(placeId = 2L)

        assertEquals(200, controller.resizeState.value!!.draftHeightDp)
        controller.setHomeHeight(heightDp = 120)
        assertEquals(120, controller.resizeState.value!!.draftHeightDp)
    }

    @Test
    fun `finishResize persists the draft height and reports one size`() = runTest {
        val mockRepository: IWidgetAreaRepository =
            createRepository(listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100)))
        val inputHomeEditState: HomeEditState = HomeEditState()
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val controller: IWidgetResizeController = createController(
            mockRepository = mockRepository,
            mockHost = mockHost,
            homeEditState = inputHomeEditState
        )
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)
        controller.dragResize(deltaDp = 8f)

        val actualFinish: ResizeFinish = controller.finishResize()!!
        val actualReport: WidgetSizeReport = controller.persistResize(finish = actualFinish)!!

        coVerify(exactly = 1) { mockRepository.setPlaceHeight(placeId = 2L, heightDp = 112) }
        assertEquals(WidgetSizeReport(listOf(AppWidgetId(9)), widthDp = 240, heightDp = 112), actualReport)
        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
    }

    @Test
    fun `finishResize at the provider default stores null`() = runTest {
        val mockRepository: IWidgetAreaRepository =
            createRepository(listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100)))
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(minHeightDp = 64, minResizeHeightDp = 48, isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)
        controller.startResize(placeId = 2L)
        val actualResizeState: ResizeState = controller.resizeState.value!!
        controller.dragResize(deltaDp = (actualResizeState.defaultHeightDp - actualResizeState.draftHeightDp).toFloat())
        assertEquals(64, controller.resizeState.value!!.draftHeightDp)

        val actualFinish: ResizeFinish = controller.finishResize()!!
        val actualReport: WidgetSizeReport = controller.persistResize(finish = actualFinish)!!

        coVerify(exactly = 1) { mockRepository.setPlaceHeight(placeId = 2L, heightDp = null) }
        assertEquals(WidgetSizeReport(listOf(AppWidgetId(9)), widthDp = 240, heightDp = 64), actualReport)
    }

    @Test
    fun `startResize is ignored while another Home edit mode is active`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val inputHomeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(
            mockRepository = mockRepository,
            mockHost = mockHost,
            homeEditState = inputHomeEditState
        )

        assertFalse(controller.startResize(placeId = 2L))

        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.Favorites, inputHomeEditState.mode.value)
    }

    @Test
    fun `cancels Resize when its place disappears without persisting`() = runTest {
        val inputRepositoryFixture: WidgetAreaRepositoryFixture = createWidgetAreaRepositoryFixture(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100))
        )
        val mockRepository: IWidgetAreaRepository = inputRepositoryFixture.mockRepository
        val inputHomeEditState: HomeEditState = HomeEditState()
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(
            mockRepository = mockRepository,
            mockHost = mockHost,
            homeEditState = inputHomeEditState
        )
        controller.setHomeHeight(heightDp = 400)
        controller.startResize(placeId = 2L)
        controller.dragResize(deltaDp = 8f)

        val inputPlaces: List<WidgetPlace> = listOf(headerPlace)
        inputRepositoryFixture.state.placesByOwner.value = mapOf(null to inputPlaces)
        controller.onPlacesChanged(places = inputPlaces)

        assertNull(controller.resizeState.value)
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
        coVerify(exactly = 0) { mockRepository.setPlaceHeight(any(), any()) }
    }

    @Test
    fun `startResize resolves a Pop-up place through its owner`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100, ownerKey = "owner-key"))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)

        assertTrue(controller.startResize(placeId = 2L, ownerKey = "owner-key"))

        assertEquals(2L, controller.resizeState.value?.placeId)
    }

    @Test
    fun `onPlacesChanged ignores Home updates while resizing a Pop-up`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 100, ownerKey = "owner-key"))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = true))
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.startResize(placeId = 2L, ownerKey = "owner-key")

        controller.onPlacesChanged(places = emptyList())

        assertEquals(2L, controller.resizeState.value?.placeId)

        controller.onPlacesChanged(places = emptyList(), ownerKey = "owner-key")

        assertNull(controller.resizeState.value)
    }

    @Test
    fun `onStackGrown clamps the stored height and reports the page size`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(stackPlace().copy(heightDp = 300)))
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        val actualReport: WidgetSizeReport = controller.onStackGrown(placeId = 1L, headerHeightDp = 100)!!

        coVerify(exactly = 1) { mockRepository.setPlaceHeight(placeId = 1L, heightDp = 200) }
        assertEquals(listOf(AppWidgetId(9)), actualReport.appWidgetIds)
    }

    @Test
    fun `onStackGrown clamps a Pop-up stack's stored height to the page bounds`() = runTest {
        val inputRepositoryFixture: WidgetAreaRepositoryFixture = createWidgetAreaRepositoryFixture(
            listOf(
                popupStackPlace(
                    placeId = 5L,
                    appWidgetIds = listOf(9, 10),
                    heightDp = 300,
                    ownerKey = "owner-key"
                )
            )
        )
        val mockRepository: IWidgetAreaRepository = inputRepositoryFixture.mockRepository
        val mockHost: IAppWidgetHostSource = createHost(
            info = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200),
            providerInfos = mapOf(10 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 160))
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        val actualReport: WidgetSizeReport = controller.onStackGrown(
            placeId = 5L,
            headerHeightDp = null,
            ownerKey = "owner-key"
        )!!

        coVerify(exactly = 1) { mockRepository.setPlaceHeight(placeId = 5L, heightDp = 160) }
        assertEquals(listOf(AppWidgetId(9), AppWidgetId(10)), actualReport.appWidgetIds)
        assertEquals(288, actualReport.heightDp)
    }

    @Test
    fun `reports the page size from the stored height`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(
                popupStackPlace(
                    placeId = 5L,
                    appWidgetIds = listOf(9, 10),
                    heightDp = 160,
                    ownerKey = "owner-key"
                )
            )
        )
        val mockHost: IAppWidgetHostSource = createHost(
            info = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200),
            providerInfos = mapOf(10 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 160))
        )
        val controller: IWidgetResizeController = createController(mockRepository = mockRepository, mockHost = mockHost)
        controller.setHomeHeight(heightDp = 400)
        controller.setWidgetAreaWidth(widthDp = 240)

        val actualReport: WidgetSizeReport = controller.onStackGrown(
            placeId = 5L,
            headerHeightDp = null,
            ownerKey = "owner-key"
        )!!

        coVerify(exactly = 0) { mockRepository.setPlaceHeight(any(), any()) }
        assertEquals(listOf(AppWidgetId(9), AppWidgetId(10)), actualReport.appWidgetIds)
        assertEquals(148, actualReport.heightDp)
    }

    private fun createController(
        mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace)),
        mockHost: IAppWidgetHostSource = createAppWidgetHostMock(),
        mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(),
        homeEditState: HomeEditState = HomeEditState()
    ): IWidgetResizeController = IWidgetResizeControllerImpl(
        repository = mockRepository,
        appWidgetHost = mockHost,
        settingsRepository = mockSettingsRepository,
        homeEditState = homeEditState
    )
}
