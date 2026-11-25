package core.ui.model.data

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class KeyboardHelpEntryLabelTest {
    @Test
    fun `resolves the help entry label and its format arguments`() {
        val inputResources: Resources = ApplicationProvider.getApplicationContext<Context>().resources
        val inputEntries: List<KeyboardHelpEntry> = listOf(
            KeyboardInputAction.StartSearch,
            KeyboardShortcut.Search,
            KeyboardShortcut.OpenRow1,
            KeyboardShortcut.KeyboardShortcutsHelp
        )
        val expectedLabels: List<String> = listOf("Start Search", "Search", "Open row 1", "Keyboard shortcuts")
        val actualLabels: List<String> = inputEntries.map { inputEntry: KeyboardHelpEntry ->
            resolveKeyboardHelpEntryLabel(entry = inputEntry, resources = inputResources)
        }
        assertEquals(expectedLabels, actualLabels)
    }
}
