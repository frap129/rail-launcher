package feature.launcher

import android.app.Dialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import core.data.testing.TEST_SDK
import core.ui.model.data.keyboardHelpEntries
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class KeyboardShortcutsHelpSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val expectedHelpShortcut: ExpectedShortcut =
        ExpectedShortcut("Keyboard shortcuts", "Ctrl + / (Shift optional)")

    private val expectedShortcuts: List<ExpectedShortcut> = listOf(
        ExpectedShortcut("Start Search", "A–Z or 0–9"),
        ExpectedShortcut("Search", "Ctrl or Meta + F"),
        ExpectedShortcut("Agenda", "Ctrl or Meta + A"),
        ExpectedShortcut("Settings", "Ctrl or Meta + S"),
        expectedHelpShortcut,
        ExpectedShortcut("Switch between Home and the App List", "Tab"),
        ExpectedShortcut("Back", "Esc"),
        ExpectedShortcut("Move Row focus", "Arrow keys"),
        ExpectedShortcut("Open focused row", "Enter / Numpad Enter / D-pad center"),
        ExpectedShortcut("Open row 1", "Ctrl or Meta + 1 / Numpad 1"),
        ExpectedShortcut("Open row 2", "Ctrl or Meta + 2 / Numpad 2"),
        ExpectedShortcut("Open row 3", "Ctrl or Meta + 3 / Numpad 3"),
        ExpectedShortcut("Open row 4", "Ctrl or Meta + 4 / Numpad 4"),
        ExpectedShortcut("Open row 5", "Ctrl or Meta + 5 / Numpad 5"),
        ExpectedShortcut("Open row 6", "Ctrl or Meta + 6 / Numpad 6"),
        ExpectedShortcut("Open row 7", "Ctrl or Meta + 7 / Numpad 7"),
        ExpectedShortcut("Open row 8", "Ctrl or Meta + 8 / Numpad 8"),
        ExpectedShortcut("Open row 9", "Ctrl or Meta + 9 / Numpad 9")
    )

    @Test
    fun `lists every shortcut as one TalkBack announcement`() {
        setHelpContent()
        assertEquals(keyboardHelpEntries.size, expectedShortcuts.size)
        expectedShortcuts.forEach { expectedShortcut: ExpectedShortcut ->
            val expectedAnnouncement: String = "${expectedShortcut.actionLabel}, ${expectedShortcut.keyCombination}"
            composeRule.onNodeWithTag(KEYBOARD_SHORTCUTS_HELP_LIST_TEST_TAG)
                .performScrollToNode(hasContentDescription(expectedAnnouncement))
            composeRule.waitForIdle()
            val actualRow: SemanticsNodeInteraction = composeRule.onNode(hasContentDescription(expectedAnnouncement))
            actualRow.assertIsDisplayed()
            val actualDescriptions: List<String> = actualRow
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .map { description: String -> description }
            assertEquals(listOf(expectedAnnouncement), actualDescriptions)
        }
    }

    @Test
    fun `focuses the sheet title when the sheet opens`() {
        setHelpContent()
        val expectedTitle: String = expectedHelpShortcut.actionLabel
        composeRule.onNode(hasText(expectedTitle) and isHeading()).assertIsFocused()
    }

    @Test
    fun `Esc dismisses the sheet`() {
        var actualDismissCount: Int = 0
        setHelpContent(onDismiss = { actualDismissCount++ })
        val expectedTitle: String = expectedHelpShortcut.actionLabel
        composeRule.onNode(hasText(expectedTitle) and isHeading()).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertEquals(1, actualDismissCount)
    }

    @Test
    fun `Back dismisses the sheet through its dialog`() {
        var actualDismissCount: Int = 0
        setHelpContent(onDismiss = { actualDismissCount++ })
        val inputDialog: Dialog = requireNotNull(ShadowDialog.getLatestDialog())
        composeRule.runOnUiThread { inputDialog.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, actualDismissCount)
    }

    private fun setHelpContent(onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            KeyboardShortcutsHelpSheet(onDismiss = onDismiss)
        }
    }

    private data class ExpectedShortcut(val actionLabel: String, val keyCombination: String)
}
