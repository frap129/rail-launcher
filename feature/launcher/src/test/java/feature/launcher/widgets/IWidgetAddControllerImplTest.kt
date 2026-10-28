package feature.launcher.widgets

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import core.data.testing.AddFlowPendingIdsState
import core.data.testing.AppWidgetHostFixture
import core.data.testing.appWidgetPlace
import core.data.testing.createAddFlowPendingIdsMock
import core.data.testing.createAppWidgetHostFixture
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.testing.headerPlace
import core.data.widgets.AddedAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IWidgetAddControllerImplTest {
    @Test
    fun `start opens the picker with providers grouped by app`() = runTest {
        val inputProviders: List<WidgetProviderInfo> = listOf(
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.zeta", className = "ZetaWidget"),
                appPackageName = "com.zeta",
                appLabel = "Zeta",
                label = "Clock"
            ),
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.alpha", className = "AlphaWidget"),
                appPackageName = "com.alpha",
                appLabel = "Alpha",
                label = "Calendar"
            ),
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.alpha", className = "BetaWidget"),
                appPackageName = "com.alpha",
                appLabel = "Alpha",
                label = "Mail"
            )
        )
        val actualController: IWidgetAddController = createAddController()
        actualController.updateProviders(providers = inputProviders)

        val actualStarted: Boolean = actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        assertTrue(actualStarted)
        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(AddWidgetOrigin(insertAt = InsertPosition.End), actualPicking.origin)
        assertEquals(listOf("Alpha", "Zeta"), actualPicking.groups.map { group -> group.appLabel })
        assertEquals(listOf("Calendar", "Mail"), actualPicking.groups.first().widgets.map { widget -> widget.label })
    }

    @Test
    fun `start lists the owner App's providers first`() = runTest {
        val inputProviders: List<WidgetProviderInfo> = listOf(
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.zeta", className = "ZetaWidget"),
                appPackageName = "com.zeta",
                appLabel = "Zeta"
            ),
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.alpha", className = "AlphaWidget"),
                appPackageName = "com.alpha",
                appLabel = "Alpha"
            )
        )
        val actualController: IWidgetAddController = createAddController()
        actualController.updateProviders(providers = inputProviders)

        actualController.start(
            origin = AddWidgetOrigin(
                insertAt = InsertPosition.End,
                ownerKey = "owner-key",
                suggestedPackageName = "com.zeta"
            )
        )

        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(listOf("Zeta", "Alpha"), actualPicking.groups.map { group -> group.appLabel })
        assertEquals(listOf(true, false), actualPicking.groups.map { group -> group.isSuggested })
    }

    @Test
    fun `an owned add places the widget in the Pop-up place without scrolling`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val actualController: IWidgetAddController = createAddController(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))

        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End, ownerKey = "owner-key"))
        actualController.pick(provider = inputProvider)

        coVerify(exactly = 1) { mockRepository.addAppWidget(match { request -> request.ownerKey == "owner-key" }) }
        actualController.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `start is ignored while another Home edit mode is active`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val actualController: IWidgetAddController = createAddController(homeEditState = inputHomeEditState)

        val actualStarted: Boolean = actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        assertFalse(actualStarted)
        assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
        assertEquals(HomeEditMode.Favorites, inputHomeEditState.mode.value)
    }

    @Test
    fun `setQuery filters the picker groups`() = runTest {
        val inputProviders: List<WidgetProviderInfo> = listOf(
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.alpha", className = "AlphaWidget"),
                appPackageName = "com.alpha",
                appLabel = "Alpha",
                label = "Calendar"
            ),
            appWidgetProviderInfo(
                provider = WidgetProvider(packageName = "com.alpha", className = "BetaWidget"),
                appPackageName = "com.alpha",
                appLabel = "Alpha",
                label = "Mail"
            )
        )
        val actualController: IWidgetAddController = createAddController()
        actualController.updateProviders(providers = inputProviders)
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        actualController.setQuery(query = "mail")

        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals("mail", actualPicking.query)
        assertEquals(listOf("Mail"), actualPicking.groups.flatMap { group -> group.widgets.map { widget -> widget.label } })

        actualController.setQuery(query = "missing")

        assertTrue((actualController.state.value as AddWidgetFlowState.Picking).groups.isEmpty())
    }

    @Test
    fun `updateProviders refreshes the groups while picking`() = runTest {
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(appLabel = "Alpha", label = "Calendar")
        val actualController: IWidgetAddController = createAddController()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        actualController.updateProviders(providers = listOf(inputProvider.copy(appLabel = "Renamed")))

        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(listOf("Renamed"), actualPicking.groups.map { group -> group.appLabel })
    }

    @Test
    fun `startFromMenu offers a placement choice below the stack cap`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val actualController: IWidgetAddController = createAddController(mockRepository = mockRepository)

        actualController.startFromMenu(target = AddWidgetTarget.Header(widgetId = null))

        val actualChoosing: AddWidgetFlowState.ChoosingPlacement =
            actualController.state.value as AddWidgetFlowState.ChoosingPlacement
        assertEquals(InsertPosition.Below(placeId = 1L), actualChoosing.origin.insertAt)
        assertTrue(actualChoosing.canAddToStack)
    }

    @Test
    fun `startFromMenu skips the choice when the stack is full`() = runTest {
        val inputStack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(
                PlacedWidget.Header(id = 1L),
                appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single()
            )
        )
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(inputStack))
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                widgetAreaSettings = WidgetAreaSettings(maxStackPages = 2)
            )
        )

        actualController.startFromMenu(target = AddWidgetTarget.Header(widgetId = 1L))

        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(InsertPosition.Below(placeId = 1L), actualPicking.origin.insertAt)
    }

    @Test
    fun `startFromMenu opens the picker at the end from the empty area`() = runTest {
        val actualController: IWidgetAddController =
            createAddController(mockRepository = createWidgetAreaRepositoryMock(places = emptyList()))

        actualController.startFromMenu(target = AddWidgetTarget.Empty)

        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(InsertPosition.End, actualPicking.origin.insertAt)
    }

    @Test
    fun `choosePlacement below keeps the place origin`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val actualController: IWidgetAddController = createAddController(mockRepository = mockRepository)
        actualController.startFromMenu(target = AddWidgetTarget.Header(widgetId = null))

        val actualStarted: Boolean = actualController.choosePlacement(placement = AddWidgetPlacement.Below)

        assertTrue(actualStarted)
        val actualPicking: AddWidgetFlowState.Picking = actualController.state.value as AddWidgetFlowState.Picking
        assertEquals(InsertPosition.Below(placeId = 1L), actualPicking.origin.insertAt)
    }

    @Test
    fun `choosePlacement into the stack appends a page and makes it current`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        actualController.updateProviders(providers = listOf(appWidgetProviderInfo()))
        actualController.startFromMenu(target = AddWidgetTarget.Header(widgetId = null))
        actualController.choosePlacement(placement = AddWidgetPlacement.IntoStack)

        actualController.pick(provider = appWidgetProviderInfo())

        coVerify(exactly = 1) {
            mockRepository.addAppWidget(match { request -> request.insertAt == InsertPosition.IntoStack(placeId = 1L) })
        }
    }

    @Test
    fun `a silent bind with no configuration places the App Widget at the end`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val mockAddFlowPendingIdsState: AddFlowPendingIdsState = AddFlowPendingIdsState()
        val mockPendingIds: IAddFlowPendingIds = createAddFlowPendingIdsMock(mockAddFlowPendingIdsState)
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost,
            mockPendingIds = mockPendingIds
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        actualController.pick(provider = inputProvider)

        coVerify(exactly = 1) {
            mockRepository.addAppWidget(
                match { request ->
                    request.appWidgetId == AppWidgetId(1) &&
                        request.provider == inputProvider.provider &&
                        request.insertAt == InsertPosition.End
                }
            )
        }
        assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
        assertEquals(listOf(AppWidgetId(1)), mockAppWidgetHostFixture.state.allocatedIds)
        assertTrue(mockAppWidgetHostFixture.state.deletedIds.isEmpty())
        assertEquals(listOf(setOf(AppWidgetId(1)), emptySet<AppWidgetId>()), mockAddFlowPendingIdsState.updates)
    }

    @Test
    fun `a refused silent bind launches the system dialog and a denial deletes the id`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(false)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

            actualController.pick(provider = inputProvider)

            val actualEvent: WidgetEvent = awaitItem()
            assertTrue(actualEvent is WidgetEvent.LaunchBind)
            actualController.onBindResult(granted = false)
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_add_widget), awaitItem())
            assertEquals(listOf(AppWidgetId(1)), mockAppWidgetHostFixture.state.deletedIds)
            coVerify(exactly = 0) { mockRepository.addAppWidget(any()) }
            assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a granted bind places the App Widget after the dialog`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(false)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        actualController.pick(provider = inputProvider)

        actualController.onBindResult(granted = true)

        coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
        assertTrue(mockAppWidgetHostFixture.state.deletedIds.isEmpty())
    }

    @Test
    fun `a provider that needs configuration launches it and cancelling deletes the id`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(needsConfiguration = true)
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

            actualController.pick(provider = inputProvider)

            assertEquals(WidgetEvent.LaunchConfigure(AppWidgetId(1)), awaitItem())
            actualController.onConfigureResult(ok = false)
            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.couldnt_add_widget), awaitItem())
            assertEquals(listOf(AppWidgetId(1)), mockAppWidgetHostFixture.state.deletedIds)
            coVerify(exactly = 0) { mockRepository.addAppWidget(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a provider that needs configuration places the App Widget when configuration succeeds`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(needsConfiguration = true)
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
            actualController.pick(provider = inputProvider)
            awaitItem()

            actualController.onConfigureResult(ok = true)

            coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a provider with optional reconfigurable configuration is placed without launching configuration`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(needsConfiguration = false)
        actualController.updateProviders(providers = listOf(inputProvider))
        coEvery { mockRepository.addAppWidget(any()) } returns AddedAppWidget(widgetId = 2L, placeId = 2L)
        actualController.events.test {
            actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

            actualController.pick(provider = inputProvider)

            assertEquals(WidgetEvent.ScrollToPlace(placeId = 2L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
    }

    @Test
    fun `cancelling the picker allocates nothing`() = runTest {
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture()
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(mockAppWidgetHost = mockHost)
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        assertTrue(actualController.state.value is AddWidgetFlowState.Picking)

        actualController.cancel()

        assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
        assertTrue(mockAppWidgetHostFixture.state.allocatedIds.isEmpty())
    }

    @Test
    fun `add from the Header menu inserts directly below the Header`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = createAppWidgetHostMock(
                providers = listOf(appWidgetProviderInfo()),
                bindResults = listOf(true)
            )
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.startFromMenu(target = AddWidgetTarget.Header(widgetId = null))
        actualController.choosePlacement(placement = AddWidgetPlacement.Below)

        actualController.pick(provider = inputProvider)

        coVerify(exactly = 1) {
            mockRepository.addAppWidget(match { request -> request.insertAt == InsertPosition.Below(placeId = 1L) })
        }
    }

    @Test
    fun `add from an App Widget menu inserts directly below that widget`() = runTest {
        val mockRepository: IWidgetAreaRepository =
            createWidgetAreaRepositoryMock(places = listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L)))
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = createAppWidgetHostMock(
                providers = listOf(appWidgetProviderInfo()),
                bindResults = listOf(true)
            )
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.startFromMenu(target = AddWidgetTarget.Widget(widgetId = 2L))
        actualController.choosePlacement(placement = AddWidgetPlacement.Below)

        actualController.pick(provider = inputProvider)

        coVerify(exactly = 1) {
            mockRepository.addAppWidget(match { request -> request.insertAt == InsertPosition.Below(placeId = 2L) })
        }
    }

    @Test
    fun `add from the empty area inserts at the end`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = emptyList())
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = createAppWidgetHostMock(
                providers = listOf(appWidgetProviderInfo()),
                bindResults = listOf(true)
            )
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.startFromMenu(target = AddWidgetTarget.Empty)

        actualController.pick(provider = inputProvider)

        coVerify(exactly = 1) {
            mockRepository.addAppWidget(match { request -> request.insertAt == InsertPosition.End })
        }
    }

    @Test
    fun `add started from Settings returns to Settings after placing`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val actualController: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = createAppWidgetHostMock(
                providers = listOf(appWidgetProviderInfo()),
                bindResults = listOf(true)
            )
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = settingsAddOrigin())

            actualController.pick(provider = inputProvider)

            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
            coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling an add started from Settings returns to Settings`() = runTest {
        val actualController: IWidgetAddController = createAddController()
        actualController.events.test {
            actualController.start(origin = settingsAddOrigin())

            actualController.cancel()

            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
            assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `denied bind for an add started from Settings returns to Settings`() = runTest {
        val actualController: IWidgetAddController = createAddController(
            mockAppWidgetHost = createAppWidgetHostMock(
                providers = listOf(appWidgetProviderInfo()),
                bindResults = listOf(false)
            )
        )
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = settingsAddOrigin())
            actualController.pick(provider = inputProvider)
            assertTrue(awaitItem() is WidgetEvent.LaunchBind)

            actualController.onBindResult(granted = false)

            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_add_widget), awaitItem())
            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling while configuring returns to Settings without an error`() = runTest {
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(mockAppWidgetHost = mockHost)
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(needsConfiguration = true)
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = settingsAddOrigin())
            actualController.pick(provider = inputProvider)
            assertEquals(WidgetEvent.LaunchConfigure(id = AppWidgetId(1)), awaitItem())

            actualController.cancel()

            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
            assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
            assertEquals(listOf(AppWidgetId(1)), mockAppWidgetHostFixture.state.deletedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling while binding returns to Settings without an error`() = runTest {
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(false)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(mockAppWidgetHost = mockHost)
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo()
        actualController.updateProviders(providers = listOf(inputProvider))
        actualController.events.test {
            actualController.start(origin = settingsAddOrigin())
            actualController.pick(provider = inputProvider)
            assertTrue(awaitItem() is WidgetEvent.LaunchBind)

            actualController.cancel()

            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
            assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
            assertEquals(listOf(AppWidgetId(1)), mockAppWidgetHostFixture.state.deletedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling an add started from the menu stays on Home`() = runTest {
        val actualController: IWidgetAddController = createAddController()
        actualController.events.test {
            actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

            actualController.cancel()

            expectNoEvents()
            assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects a pick when the provider is no longer offered`() = runTest {
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo())
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val actualController: IWidgetAddController = createAddController(mockAppWidgetHost = mockHost)
        actualController.updateProviders(providers = listOf(appWidgetProviderInfo()))
        actualController.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        actualController.updateProviders(providers = emptyList())
        actualController.events.test {
            actualController.pick(provider = appWidgetProviderInfo())

            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.couldnt_add_widget), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
        assertTrue(mockAppWidgetHostFixture.state.allocatedIds.isEmpty())
    }

    @Test
    fun `restores a pending configuration after process recreation`() = runTest {
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace))
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(true)
        )
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val inputSavedStateHandle: SavedStateHandle = SavedStateHandle()
        val actualFirst: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost,
            savedStateHandle = inputSavedStateHandle
        )
        actualFirst.updateProviders(providers = listOf(appWidgetProviderInfo()))
        actualFirst.start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        actualFirst.pick(provider = appWidgetProviderInfo(needsConfiguration = true))
        assertTrue(actualFirst.state.value is AddWidgetFlowState.Configuring)

        val mockAddFlowPendingIdsState: AddFlowPendingIdsState = AddFlowPendingIdsState()
        val mockPendingIds: IAddFlowPendingIds = createAddFlowPendingIdsMock(mockAddFlowPendingIdsState)
        val actualRestored: IWidgetAddController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost,
            savedStateHandle = inputSavedStateHandle,
            mockPendingIds = mockPendingIds
        )
        actualRestored.restore()

        assertTrue(actualRestored.state.value is AddWidgetFlowState.Configuring)
        assertEquals(setOf(AppWidgetId(1)), mockAddFlowPendingIdsState.ids)

        actualRestored.onConfigureResult(ok = true)

        coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
        assertTrue(mockAddFlowPendingIdsState.ids.isEmpty())
    }

    @Test
    fun `restore fails an incomplete pending widget and keeps its ids clear`() = runTest {
        val mockAppWidgetHostFixture: AppWidgetHostFixture = createAppWidgetHostFixture()
        val mockHost: IAppWidgetHostSource = mockAppWidgetHostFixture.mockAppWidgetHost
        val mockAddFlowPendingIdsState: AddFlowPendingIdsState = AddFlowPendingIdsState()
        val mockPendingIds: IAddFlowPendingIds = createAddFlowPendingIdsMock(mockAddFlowPendingIdsState)
        val inputSavedStateHandle: SavedStateHandle = SavedStateHandle()
        inputSavedStateHandle[IWidgetAddControllerImpl.PENDING_APP_WIDGET_ID_KEY] = 7
        val actualController: IWidgetAddController = createAddController(
            mockAppWidgetHost = mockHost,
            savedStateHandle = inputSavedStateHandle,
            mockPendingIds = mockPendingIds
        )

        actualController.events.test {
            actualController.restore()

            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.couldnt_add_widget), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(AppWidgetId(7)), mockAppWidgetHostFixture.state.deletedIds)
        assertTrue(mockAddFlowPendingIdsState.ids.isEmpty())
        assertEquals(AddWidgetFlowState.Idle, actualController.state.value)
    }
}
