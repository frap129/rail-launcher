package core.data.search

import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.prefs.Settings
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_SDK
import core.data.testing.createPausedWorkApp
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AutoLaunchTest {
    private val inputAutoLaunchOn: Settings = Settings(searchAutoLaunch = true)

    @Test
    fun `opens the only eligible Launchable or Shortcut result`() {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages")
        val cases: List<AutoLaunchCase> = listOf(
            AutoLaunchCase(
                description = "a Launchable",
                results = listOf(SearchResult.LaunchableResult(inputLaunchable)),
                expectedResult = SearchResult.LaunchableResult(inputLaunchable)
            ),
            AutoLaunchCase(
                description = "a Shortcut",
                results = listOf(SearchResult.LaunchableResult(inputShortcut)),
                expectedResult = SearchResult.LaunchableResult(inputShortcut)
            ),
            AutoLaunchCase(
                description = "a Launchable followed by the external rows",
                results = listOf(
                    SearchResult.LaunchableResult(inputLaunchable),
                    SearchResult.TypedUrlRow(url = "https://alpha.example"),
                    SearchResult.WebSearchRow(url = "https://www.google.com/search?q=alpha"),
                    SearchResult.PlayStoreRow(
                        marketUrl = "market://search?q=alpha",
                        webUrl = "https://play.google.com/store/search?q=alpha"
                    )
                ),
                expectedResult = SearchResult.LaunchableResult(inputLaunchable)
            )
        )

        cases.forEach { case ->
            assertEquals(case.description, case.expectedResult, case.results.autoLaunchResult(settings = inputAutoLaunchOn))
        }
    }

    @Test
    fun `holds Auto-launch back in every other state`() {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        val inputCalculatorQuery: Launchable = createRailLaunchable("12*7")
        val cases: List<AutoLaunchCase> = listOf(
            AutoLaunchCase(
                description = "two Launchables",
                results = listOf(
                    SearchResult.LaunchableResult(inputLaunchable),
                    SearchResult.LaunchableResult(createRailLaunchable("Alfred"))
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Calculator row",
                results = listOf(
                    SearchResult.LaunchableResult(inputCalculatorQuery),
                    SearchResult.CalculatorRow(expression = "12*7", value = "84")
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Paused result",
                results = listOf(SearchResult.LaunchableResult(createPausedWorkApp())),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a disabled Shortcut",
                results = listOf(
                    SearchResult.LaunchableResult(createShortcut(packageName = "com.example.site", isEnabled = false))
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Folder",
                results = listOf(SearchResult.LaunchableResult(Folder(folderId = 1L, name = "Games"))),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "no Launchable result",
                results = listOf(SearchResult.CalculatorRow(expression = "12*7", value = "84")),
                expectedResult = null
            )
        )

        cases.forEach { case ->
            assertEquals(case.description, case.expectedResult, case.results.autoLaunchResult(settings = inputAutoLaunchOn))
        }
    }

    @Test
    fun `does not open a result when Auto-launch is off`() {
        val inputResults: List<SearchResult> = listOf(SearchResult.LaunchableResult(createRailLaunchable("Alpha")))

        val actualResult: SearchResult.LaunchableResult? = inputResults.autoLaunchResult(settings = Settings())

        assertNull(actualResult)
    }
}

private data class AutoLaunchCase(
    val description: String,
    val results: List<SearchResult>,
    val expectedResult: SearchResult.LaunchableResult?
)
