package core.data.search

import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_SDK
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcut
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class SearchResultTest {
    private val inputLocale: Locale = Locale.US
    private val inputEmptyState: LaunchableState = createLoadedLaunchableState(emptyList())

    @Test
    fun `keeps an App named 99 as the Top result`() {
        val inputApp: Launchable = createRailLaunchable("99")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))

        val actualResults: List<SearchResult> = inputState.searchResultRows(
            query = "99",
            launchCounts = emptyMap(),
            shortcuts = emptyList(),
            locale = inputLocale
        )

        assertEquals(listOf<SearchResult>(SearchResult.LaunchableResult(inputApp)), actualResults)
    }

    @Test
    fun `appends the Calculator row after the Launchable and Shortcut results`() {
        val inputApp: Launchable = createRailLaunchable("2+2")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.calculator", shortLabel = "2+2")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))
        val inputShortcuts: List<SearchShortcut> = listOf(
            SearchShortcut(shortcut = inputShortcut, publisherName = "Calculator", isHidden = false)
        )

        val actualResults: List<SearchResult> = inputState.searchResultRows(
            query = "2+2",
            launchCounts = emptyMap(),
            shortcuts = inputShortcuts,
            locale = inputLocale
        )

        val expectedShortcut: ShortcutLaunchable = inputShortcut.copy(publisherName = "Calculator")
        val expectedCalculatorRow: SearchResult = SearchResult.CalculatorRow(expression = "2+2", value = "4")
        assertEquals(
            listOf(SearchResult.LaunchableResult(inputApp), SearchResult.LaunchableResult(expectedShortcut), expectedCalculatorRow),
            actualResults
        )
    }

    @Test
    fun `makes the Calculator row the Top result when nothing else matches`() {
        val actualResults: List<SearchResult> = inputEmptyState.searchResultRows(
            query = "12*7",
            launchCounts = emptyMap(),
            shortcuts = emptyList(),
            locale = inputLocale
        )

        assertEquals(listOf<SearchResult>(SearchResult.CalculatorRow(expression = "12*7", value = "84")), actualResults)
    }

    @Test
    fun `adds no row when the Calculator check rejects the query`() {
        val actualResults: List<SearchResult> = inputEmptyState.searchResultRows(
            query = "1/0",
            launchCounts = emptyMap(),
            shortcuts = emptyList(),
            locale = inputLocale
        )

        assertTrue(actualResults.isEmpty())
    }
}
