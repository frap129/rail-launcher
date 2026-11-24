package feature.launcher

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import core.data.testing.TEST_SDK
import core.ui.model.data.KeyboardShortcut
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class KeyboardShortcutsHelpSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val expectedShortcuts: Map<KeyboardShortcut, ExpectedShortcut> = linkedMapOf(
        KeyboardShortcut.Search to ExpectedShortcut("Search", "Ctrl or Meta + F"),
        KeyboardShortcut.Agenda to ExpectedShortcut("Agenda", "Ctrl or Meta + A"),
        KeyboardShortcut.Settings to ExpectedShortcut("Settings", "Ctrl or Meta + S"),
        KeyboardShortcut.KeyboardShortcutsHelp to ExpectedShortcut("Keyboard shortcuts", "Ctrl or Meta + / (Shift optional)"),
        KeyboardShortcut.ToggleAppList to ExpectedShortcut("Switch between Home and the App List", "Tab"),
        KeyboardShortcut.OpenRow1 to ExpectedShortcut("Open row 1", "Ctrl or Meta + 1 / Numpad 1"),
        KeyboardShortcut.OpenRow2 to ExpectedShortcut("Open row 2", "Ctrl or Meta + 2 / Numpad 2"),
        KeyboardShortcut.OpenRow3 to ExpectedShortcut("Open row 3", "Ctrl or Meta + 3 / Numpad 3"),
        KeyboardShortcut.OpenRow4 to ExpectedShortcut("Open row 4", "Ctrl or Meta + 4 / Numpad 4"),
        KeyboardShortcut.OpenRow5 to ExpectedShortcut("Open row 5", "Ctrl or Meta + 5 / Numpad 5"),
        KeyboardShortcut.OpenRow6 to ExpectedShortcut("Open row 6", "Ctrl or Meta + 6 / Numpad 6"),
        KeyboardShortcut.OpenRow7 to ExpectedShortcut("Open row 7", "Ctrl or Meta + 7 / Numpad 7"),
        KeyboardShortcut.OpenRow8 to ExpectedShortcut("Open row 8", "Ctrl or Meta + 8 / Numpad 8"),
        KeyboardShortcut.OpenRow9 to ExpectedShortcut("Open row 9", "Ctrl or Meta + 9 / Numpad 9")
    )

    @Test
    fun `lists every shortcut as one TalkBack announcement`() {
        setHelpContent()
        assertEquals(KeyboardShortcut.entries.toSet(), expectedShortcuts.keys)
        expectedShortcuts.entries.forEachIndexed { index: Int, inputEntry: Map.Entry<KeyboardShortcut, ExpectedShortcut> ->
            val expectedShortcut: ExpectedShortcut = inputEntry.value
            val expectedAnnouncement: String = "${expectedShortcut.actionLabel}, ${expectedShortcut.keyCombination}"
            composeRule.onNodeWithTag(KEYBOARD_SHORTCUTS_HELP_LIST_TEST_TAG).performScrollToIndex(index)
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
        val expectedTitle: String = expectedShortcuts.getValue(KeyboardShortcut.KeyboardShortcutsHelp).actionLabel
        composeRule.onNode(hasText(expectedTitle) and isHeading()).assertIsFocused()
    }

    @Test
    fun `Esc dismisses the sheet`() {
        var actualDismissCount: Int = 0
        setHelpContent(onDismiss = { actualDismissCount++ })
        val expectedTitle: String = expectedShortcuts.getValue(KeyboardShortcut.KeyboardShortcutsHelp).actionLabel
        composeRule.onNode(hasText(expectedTitle) and isHeading()).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertEquals(1, actualDismissCount)
    }

    @Test
    fun `Back dismisses the sheet`() {
        var actualDismissCount: Int = 0
        lateinit var actualBackDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            val inputOwner: OnBackPressedDispatcherOwner? = LocalOnBackPressedDispatcherOwner.current
            actualBackDispatcher = requireNotNull(inputOwner).onBackPressedDispatcher
            KeyboardShortcutsHelpSheet(onDismiss = { actualDismissCount++ })
        }
        composeRule.runOnUiThread { actualBackDispatcher.onBackPressed() }
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
