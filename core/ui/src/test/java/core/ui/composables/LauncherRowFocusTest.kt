package core.ui.composables

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.ui.model.data.LauncherInputState
import core.ui.popups.PopupRow
import core.ui.testing.createLauncherItemActions
import core.ui.testing.testFirstApp
import core.ui.testing.testSecondApp
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
private val NON_ROW_FOCUS_SIZE: Dp = 48.dp
private const val ROW_BACKGROUND_SAMPLE_INSET_PX: Int = 20

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherRowFocusTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `Down moves row focus to the next Home row`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `Down gives row focus to the first App List row when no row has it`() {
        composeRule.setAppListRows(itemActions = createLauncherItemActions())
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `Down gives row focus to the first Home row when no row has it`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `Up gives row focus to the last Home row when no row has it`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.row(name = "Beta").assertIsFocused()
    }

    @Test
    fun `Up moves row focus to the previous App List row`() {
        composeRule.setAppListRows(itemActions = createLauncherItemActions())
        composeRule.row(name = "Beta").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.row(name = "Alpha").assertIsFocused()
    }

    @Test
    fun `keeps row focus on one Home row at a time`() {
        composeRule.setHomeSuggestionRows(itemActions = createLauncherItemActions())
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.row(name = "Alpha").assertIsNotFocused()
    }

    @Test
    fun `Enter opens the focused Home row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.row(name = "Alpha").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testFirstApp), actualOpens)
    }

    @Test
    fun `Enter opens the focused App List row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setAppListRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        composeRule.row(name = "Beta").requestFocus()
        composeRule.row(name = "Beta").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(testSecondApp), actualOpens)
    }

    @Test
    fun `modified Enter leaves the focused row alone`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualOpens += launchable })
        )
        val actualRow: SemanticsNodeInteraction = composeRule.row(name = "Alpha")
        actualRow.requestFocus()
        listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_ALT_ON, KeyEvent.META_META_ON).forEach { inputMetaState ->
            composeRule.pressKey(keyCode = KeyEvent.KEYCODE_ENTER, metaState = inputMetaState)
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
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val actualRow: SemanticsNodeInteraction = composeRule.row(name = "Beta")
        val actualNodeId: Int = actualRow.fetchSemanticsNode().id
        val actualView: View = composeRule.rootView()
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
        composeRule.row(name = "Alpha").assertIsNotFocused()
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
                        .size(NON_ROW_FOCUS_SIZE)
                        .focusIndicator()
                        .clickable(onClick = {})
                )
            }
        }
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val actualControl: SemanticsNodeInteraction = composeRule.onNodeWithTag(NON_ROW_FOCUS_TEST_TAG)
        val actualNodeId: Int = actualControl.fetchSemanticsNode().id
        val actualView: View = composeRule.rootView()
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
        composeRule.row(name = "Alpha").assertIsFocused()
        actualControl.assertIsNotFocused()
    }

    @Test
    fun `keeps Search result rows out of Row focus`() {
        composeRule.setLauncherItemRow(item = testFirstApp, popupRow = PopupRow.Search, isRowFocusEnabled = false)
        composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG).assert(isNotFocusable())
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
        composeRule.row(name = "Alpha").requestFocus()
        composeRule.waitForIdle()
        assertFalse(inputLauncherInputState.isTextFieldFocused.value)
    }

    private fun enableAccessibility() {
        val accessibilityManager: AccessibilityManager = RuntimeEnvironment.getApplication().getSystemService(
            AccessibilityManager::class.java
        )
        shadowOf(accessibilityManager).setEnabled(true)
        shadowOf(accessibilityManager).setTouchExplorationEnabled(true)
    }

    private fun accessibilityFocus(): AccessibilityNodeInfo? {
        val actualView: View = composeRule.rootView()
        return actualView.accessibilityNodeProvider?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    private fun SemanticsNodeInteraction.rowBackgroundColor(): Color =
        captureToImage().toPixelMap().let { pixels -> pixels[pixels.width - ROW_BACKGROUND_SAMPLE_INSET_PX, pixels.height / 2] }
}
