package feature.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasRequestFocusAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.RailSide
import core.data.testing.TEST_SDK
import core.ui.IHomeIconsDrawn
import core.ui.composables.row
import core.ui.composables.scrollrail.RailBounds
import core.ui.layout.HomeEditMode
import core.ui.testing.createLauncherItemActions
import core.ui.testing.testFirstApp
import core.ui.testing.testSecondApp
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.createWidgetAreaViewModelMock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherContentFocusTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `Down moves row focus to the next Home Favorite`() {
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp, testSecondApp),
            itemActions = createLauncherItemActions()
        )
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `Enter only opens rows on the visible page after each switch`() {
        val actualOpened: MutableList<Launchable> = mutableListOf()
        val inputHomeState: LauncherUiState.Home = createHomeState(favorites = listOf(testFirstApp))
        val inputUiState: MutableStateFlow<LauncherUiState> = MutableStateFlow(inputHomeState)
        setSwitchingLauncherContent(inputUiState = inputUiState, actualOpened = actualOpened)
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            inputUiState.value = LauncherUiState.AppList.Browsing(
                groups = listOf(LaunchableGroup.InitialGroup(label = "B", items = listOf(testSecondApp))),
                recentlyInstalled = null,
                lazyListState = LazyListState(firstVisibleItemIndex = 1),
                isEditingFavorites = false
            )
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertTrue(actualOpened.isEmpty())
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Beta").assertIsFocused()
        composeRule.row(name = "Beta").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp), actualOpened)
        composeRule.runOnIdle { inputUiState.value = inputHomeState }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp), actualOpened)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Alpha").assertIsFocused()
        composeRule.row(name = "Alpha").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp, testFirstApp), actualOpened)
    }

    @Test
    fun `keeps Home rows out of Row focus while widgets are edited`() {
        assertHomeRowsAreNotFocusable(isEditingWidgets = true)
    }

    @Test
    fun `keeps Home rows out of Row focus while widgets are resized`() {
        assertHomeRowsAreNotFocusable(isResizing = true)
    }

    private fun setSwitchingLauncherContent(inputUiState: MutableStateFlow<LauncherUiState>, actualOpened: MutableList<Launchable>) {
        val mockLauncherViewModel: LauncherViewModel = createLauncherViewModelMock(inputUiState = inputUiState)
        val mockWidgetAreaViewModel: WidgetAreaViewModel = createWidgetAreaViewModelMock()
        val mockHomeIconsDrawn: IHomeIconsDrawn = mockk(relaxed = true)
        composeRule.setContent {
            val inputRailBounds: RailBounds = remember { RailBounds() }
            LauncherContent(
                modifier = Modifier.fillMaxSize(),
                viewModel = mockLauncherViewModel,
                widgetViewModel = mockWidgetAreaViewModel,
                railBounds = inputRailBounds,
                railSide = RailSide.DEFAULT,
                showRail = false,
                onItemLaunch = { launchable: Launchable -> actualOpened += launchable },
                onRowTap = { launchable: Launchable, _ -> actualOpened += launchable },
                onOpenAgenda = {},
                onRequestHomeRole = {},
                onItemOpenPopup = { _, _ -> },
                onItemAnchorChanged = { _, _ -> },
                onCopyCalculatorRow = {},
                onOpenSearchRow = {},
                onContactRowAction = { _, _ -> },
                onOpenSearchSettings = {},
                homeIconsDrawn = mockHomeIconsDrawn,
                isResizing = false,
                isEditingWidgets = false,
                homeEditMode = HomeEditMode.None
            )
        }
    }

    private fun createLauncherViewModelMock(inputUiState: MutableStateFlow<LauncherUiState>): LauncherViewModel {
        val mockLauncherViewModel: LauncherViewModel = mockk(relaxed = true)
        every { mockLauncherViewModel.uiState } returns inputUiState
        every { mockLauncherViewModel.folderSummaryLines } returns MutableStateFlow(emptyMap())
        every { mockLauncherViewModel.privateSpaceSettingsIntent } returns MutableStateFlow(null)
        every { mockLauncherViewModel.favorites } returns flowOf(emptyList())
        every { mockLauncherViewModel.listStartOffsetPx } returns 0
        every { mockLauncherViewModel.listEndOffsetPx } returns 0
        return mockLauncherViewModel
    }

    private fun assertHomeRowsAreNotFocusable(isResizing: Boolean = false, isEditingWidgets: Boolean = false) {
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp, testSecondApp),
            itemActions = createLauncherItemActions(),
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets
        )
        composeRule.onNode(
            hasRequestFocusAction() and hasAnyDescendant(hasText("Alpha")),
            useUnmergedTree = true
        ).assertDoesNotExist()
    }
}
