package feature.launcher

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasRequestFocusAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.RailSide
import core.data.testing.TEST_SDK
import core.ui.IHomeIconsDrawn
import core.ui.composables.LocalLauncherInputState
import core.ui.composables.RowTestContent
import core.ui.composables.assertPaintsTopEdge
import core.ui.composables.captureThemeColor
import core.ui.composables.focusIndicator
import core.ui.composables.rowTestBackgroundColor
import core.ui.composables.scrollrail.RailBounds
import core.ui.layout.HomeEditMode
import core.ui.model.data.LauncherInputState
import core.ui.popups.PopupRow
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.WidgetAreaViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NON_ROW_FOCUS_TEST_TAG: String = "nonRowFocus"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherRowFocusTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `Down moves row focus to the next Home row`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        row(name = "Alpha").requestFocus()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `Down gives row focus to the first App List row when no row has it`() {
        composeRule.setAppListRows(itemActions = createLauncherItemActions())

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `Down gives row focus to the first Home row when no row has it`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `Up gives row focus to the last Home row when no row has it`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }

        row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `Up moves row focus to the previous App List row`() {
        composeRule.setAppListRows(itemActions = createLauncherItemActions())
        row(name = "Beta").requestFocus()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }

        row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `Down moves row focus to the next Home Favorite`() {
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp, testSecondApp),
            itemActions = createLauncherItemActions()
        )
        row(name = "Alpha").requestFocus()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `keeps row focus on one Home row at a time`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        row(name = "Alpha").requestFocus()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        row(name = "Alpha").assertIsNotFocused()
    }

    @Test
    fun `Enter opens the focused Home row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        row(name = "Alpha").requestFocus()

        row(name = "Alpha").performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(testFirstApp), actualOpens)
    }

    @Test
    fun `Enter opens the focused App List row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setAppListRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        row(name = "Beta").requestFocus()

        row(name = "Beta").performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(testSecondApp), actualOpens)
    }

    @Test
    fun `Enter only opens rows on the visible page after each switch`() {
        val actualOpened: MutableList<Launchable> = mutableListOf()
        val inputHomeState: LauncherUiState.Home = createHomeState(favorites = listOf(testFirstApp))
        val inputUiState: MutableStateFlow<LauncherUiState> = MutableStateFlow(inputHomeState)
        setSwitchingLauncherContent(inputUiState = inputUiState, actualOpened = actualOpened)
        row(name = "Alpha").requestFocus()
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
        row(name = "Beta").assertIsFocused()
        row(name = "Beta").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp), actualOpened)
        composeRule.runOnIdle { inputUiState.value = inputHomeState }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp), actualOpened)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        row(name = "Alpha").assertIsFocused()
        row(name = "Alpha").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp, testFirstApp), actualOpened)
    }

    @Test
    fun `modified Enter leaves the focused row alone`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        val actualRow: SemanticsNodeInteraction = row(name = "Alpha")
        actualRow.requestFocus()

        listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_ALT_ON, KeyEvent.META_META_ON).forEach { inputMetaState ->
            pressKey(keyCode = KeyEvent.KEYCODE_ENTER, metaState = inputMetaState)
        }

        assertTrue(actualOpens.isEmpty())
    }

    @Test
    fun `paints a Focus indicator on the focused row`() {
        val expectedFocusColor: Color = composeRule.captureThemeColor(
            color = { MaterialTheme.colorScheme.primary },
            content = { LauncherItemTestContent(item = testFirstApp) }
        )
        val actualRow: SemanticsNodeInteraction = composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG)
        actualRow.assertPaintsTopEdge(expectedColor = rowTestBackgroundColor)

        actualRow.requestFocus()
        composeRule.waitForIdle()

        actualRow.assertPaintsTopEdge(expectedColor = expectedFocusColor)
    }

    @Test
    fun `a hovered focused row paints the focus background only`() {
        composeRule.setLauncherItemRow(item = testFirstApp)
        val actualRow: SemanticsNodeInteraction = composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG)
        actualRow.requestFocus()
        composeRule.waitForIdle()
        val expectedFocusBackground: Color = actualRow.rowBackgroundColor()

        actualRow.performMouseInput { moveTo(center) }
        composeRule.waitForIdle()

        assertEquals(expectedFocusBackground, actualRow.rowBackgroundColor())
    }

    @Test
    fun `paints the same Focus indicator on a clickable row`() {
        val expectedFocusColor: Color = composeRule.captureThemeColor(
            color = { MaterialTheme.colorScheme.primary },
            content = {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(inputModeManager) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                LauncherActionRowTestContent()
            }
        )
        val actualRow: SemanticsNodeInteraction = composeRule.onNodeWithTag(LAUNCHER_ACTION_ROW_TEST_TAG)

        actualRow.requestFocus()
        composeRule.waitForIdle()

        actualRow.assertPaintsTopEdge(expectedColor = expectedFocusColor)
    }

    @Test
    fun `moves TalkBack focus to the row keyboard focus moves to`() {
        enableAccessibility()
        composeRule.setLauncherItemRow(item = testFirstApp)
        val actualRow: SemanticsNodeInteraction = composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG)

        actualRow.requestFocus()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val actualAccessibilityFocus: AccessibilityNodeInfo? = accessibilityFocus()
        assertNotNull(actualAccessibilityFocus)
        assertTrue(requireNotNull(actualAccessibilityFocus).isAccessibilityFocused)
        assertEquals(testFirstApp.accessibilityName, actualAccessibilityFocus?.contentDescription?.toString())
        assertNull(actualAccessibilityFocus?.text)
    }

    @Test
    fun `moving TalkBack focus to a row moves Row focus to it`() {
        enableAccessibility()
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val actualRow: SemanticsNodeInteraction = row(name = "Beta")
        val actualNodeId: Int = actualRow.fetchSemanticsNode().id
        val actualView: View = getRootView()
        composeRule.runOnIdle {
            val actualFocusResult: Boolean = actualView.accessibilityNodeProvider?.performAction(
                actualNodeId,
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null
            ) == true
            assertTrue(actualFocusResult)
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        actualRow.assertIsFocused()
        row(name = "Alpha").assertIsNotFocused()
    }

    @Test
    fun `TalkBack focus on a non-row control leaves Row focus alone`() {
        enableAccessibility()
        composeRule.setContent {
            RowTestContent {
                HomeSuggestionRowsTestContent(itemActions = createLauncherItemActions())
                Box(
                    modifier = Modifier
                        .testTag(NON_ROW_FOCUS_TEST_TAG)
                        .size(48.dp)
                        .focusIndicator()
                        .clickable(onClick = {})
                )
            }
        }
        row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val actualControl: SemanticsNodeInteraction = composeRule.onNodeWithTag(NON_ROW_FOCUS_TEST_TAG)
        val actualNodeId: Int = actualControl.fetchSemanticsNode().id
        val actualView: View = getRootView()
        composeRule.runOnIdle {
            val actualFocusResult: Boolean = actualView.accessibilityNodeProvider?.performAction(
                actualNodeId,
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null
            ) == true
            assertTrue(actualFocusResult)
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        row(name = "Alpha").assertIsFocused()
        actualControl.assertIsNotFocused()
    }

    @Test
    fun `keeps Search result rows out of Row focus`() {
        composeRule.setLauncherItemRow(item = testFirstApp, popupRow = PopupRow.Search, isRowFocusEnabled = false)

        composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG).assert(isNotFocusable())
    }

    @Test
    fun `keeps Home rows out of Row focus while widgets are edited`() {
        assertHomeRowsAreNotFocusable(isEditingWidgets = true)
    }

    @Test
    fun `keeps Home rows out of Row focus while widgets are resized`() {
        assertHomeRowsAreNotFocusable(isResizing = true)
    }

    @Test
    fun `keeps row focus from reporting text field focus`() {
        val inputLauncherInputState: LauncherInputState = LauncherInputState()
        val inputItemActions: LauncherItemActions = createLauncherItemActions()
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherInputState provides inputLauncherInputState) {
                RowTestContent {
                    HomeSuggestionRowsTestContent(itemActions = inputItemActions)
                }
            }
        }

        row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()

        assertFalse(inputLauncherInputState.isTextFieldFocused.value)
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

    private fun createWidgetAreaViewModelMock(): WidgetAreaViewModel {
        val inputWidgetAreaUi: WidgetAreaUi = createTestWidgetAreaUi()
        val mockWidgetAreaViewModel: WidgetAreaViewModel = mockk(relaxed = true)
        every { mockWidgetAreaViewModel.widgetAreaState } returns MutableStateFlow(inputWidgetAreaUi.state)
        every { mockWidgetAreaViewModel.resizeState } returns MutableStateFlow(null)
        every { mockWidgetAreaViewModel.editState } returns MutableStateFlow(null)
        every { mockWidgetAreaViewModel.mediaPlayerState } returns MutableStateFlow(inputWidgetAreaUi.mediaPlayer)
        every { mockWidgetAreaViewModel.scrollToPlaceId } returns MutableStateFlow(null)
        return mockWidgetAreaViewModel
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

    private fun enableAccessibility() {
        val accessibilityManager: AccessibilityManager = RuntimeEnvironment.getApplication().getSystemService(
            AccessibilityManager::class.java
        )
        shadowOf(accessibilityManager).setEnabled(true)
        shadowOf(accessibilityManager).setTouchExplorationEnabled(true)
    }

    private fun accessibilityFocus(): AccessibilityNodeInfo? {
        val actualView: View = getRootView()
        return actualView.accessibilityNodeProvider?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    private fun pressKey(keyCode: Int, metaState: Int) {
        val actualView: View = getRootView()
        composeRule.runOnIdle {
            actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_DOWN, keyCode = keyCode, metaState = metaState))
            actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_UP, keyCode = keyCode, metaState = metaState))
        }
    }

    private fun getRootView(): View = (requireNotNull(composeRule.onRoot().fetchSemanticsNode().root) as ViewRootForTest).view

    private fun createKeyEvent(action: Int, keyCode: Int, metaState: Int): KeyEvent = KeyEvent(
        0L,
        0L,
        action,
        keyCode,
        0,
        metaState,
        0,
        0,
        0
    )

    private fun SemanticsNodeInteraction.rowBackgroundColor(): Color =
        captureToImage().toPixelMap().let { pixels -> pixels[pixels.width - 20, pixels.height / 2] }

    private fun row(name: String): SemanticsNodeInteraction = composeRule.onNode(
        hasRequestFocusAction() and hasAnyDescendant(hasText(name)),
        useUnmergedTree = true
    )
}
