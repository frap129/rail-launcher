package feature.launcher

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewRootForTest
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
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.ui.composables.LocalLauncherInputState
import core.ui.composables.RowTestContent
import core.ui.composables.assertPaintsTopEdge
import core.ui.composables.captureThemeColor
import core.ui.composables.rowTestBackgroundColor
import core.ui.model.data.LauncherInputState
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
    fun `modified Enter leaves the focused row alone`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        val actualRow: SemanticsNodeInteraction = row(name = "Alpha")
        actualRow.requestFocus()

        listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_ALT_ON, KeyEvent.META_META_ON).forEach { inputMetaState ->
            actualRow.pressKey(keyCode = KeyEvent.KEYCODE_ENTER, metaState = inputMetaState)
        }

        assertTrue(actualOpens.isEmpty())
    }

    @Test
    fun `paints a focus indicator on the focused row`() {
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
    fun `paints the same focus indicator on a clickable row`() {
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

        val actualAccessibilityFocus: AccessibilityNodeInfo? = actualRow.accessibilityFocus()
        assertNotNull(actualAccessibilityFocus)
        assertTrue(requireNotNull(actualAccessibilityFocus).isAccessibilityFocused)
        assertEquals(testFirstApp.accessibilityName, actualAccessibilityFocus?.contentDescription?.toString())
        assertNull(actualAccessibilityFocus?.text)
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

    private fun SemanticsNodeInteraction.accessibilityFocus(): AccessibilityNodeInfo? {
        val actualView: View = (requireNotNull(fetchSemanticsNode().root) as ViewRootForTest).view
        return actualView.accessibilityNodeProvider?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    private fun SemanticsNodeInteraction.pressKey(keyCode: Int, metaState: Int) {
        val actualView: View = (requireNotNull(fetchSemanticsNode().root) as ViewRootForTest).view
        composeRule.runOnIdle {
            actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_DOWN, keyCode = keyCode, metaState = metaState))
            actualView.dispatchKeyEvent(createKeyEvent(action = KeyEvent.ACTION_UP, keyCode = keyCode, metaState = metaState))
        }
    }

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
