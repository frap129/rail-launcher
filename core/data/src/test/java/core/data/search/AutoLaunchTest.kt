package core.data.search

import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.prefs.Settings
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_CALCULATOR_QUERY
import core.data.testing.TEST_SDK
import core.data.testing.createLaunchableResult
import core.data.testing.createPausedWorkApp
import core.data.testing.createRailLaunchable
import core.data.testing.createSearchContact
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
                results = listOf(createLaunchableResult(inputLaunchable)),
                expectedResult = createLaunchableResult(inputLaunchable)
            ),
            AutoLaunchCase(
                description = "a Shortcut",
                results = listOf(createLaunchableResult(inputShortcut)),
                expectedResult = createLaunchableResult(inputShortcut)
            ),
            AutoLaunchCase(
                description = "a Launchable followed by the external rows",
                results = listOf(
                    createLaunchableResult(inputLaunchable),
                    SearchResult.TypedUrlRow(url = "https://alpha.example"),
                    SearchResult.WebSearchRow(url = "https://www.google.com/search?q=alpha"),
                    SearchResult.PlayStoreRow(
                        marketUrl = "market://search?q=alpha",
                        webUrl = "https://play.google.com/store/search?q=alpha"
                    )
                ),
                expectedResult = createLaunchableResult(inputLaunchable)
            )
        )

        cases.forEach { case ->
            assertEquals(case.description, case.expectedResult, case.results.autoLaunchResult(settings = inputAutoLaunchOn))
        }
    }

    @Test
    fun `holds Auto-launch back in every other state`() {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        val inputCalculatorQuery: Launchable = createRailLaunchable(TEST_CALCULATOR_QUERY)
        val cases: List<AutoLaunchCase> = listOf(
            AutoLaunchCase(
                description = "two Launchables",
                results = listOf(
                    createLaunchableResult(inputLaunchable),
                    createLaunchableResult(createRailLaunchable("Alfred"))
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Calculator row",
                results = listOf(
                    createLaunchableResult(inputCalculatorQuery),
                    SearchResult.CalculatorRow(expression = TEST_CALCULATOR_QUERY, value = "84")
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Contact row",
                results = listOf(
                    createLaunchableResult(inputLaunchable),
                    SearchResult.ContactRow(contact = createSearchContact())
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Paused result",
                results = listOf(createLaunchableResult(createPausedWorkApp())),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a disabled Shortcut",
                results = listOf(
                    createLaunchableResult(createShortcut(packageName = "com.example.site", isEnabled = false))
                ),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "a Folder",
                results = listOf(createLaunchableResult(Folder(folderId = 1L, name = "Games"))),
                expectedResult = null
            ),
            AutoLaunchCase(
                description = "no Launchable result",
                results = listOf(SearchResult.CalculatorRow(expression = TEST_CALCULATOR_QUERY, value = "84")),
                expectedResult = null
            )
        )

        cases.forEach { case ->
            assertEquals(case.description, case.expectedResult, case.results.autoLaunchResult(settings = inputAutoLaunchOn))
        }
    }

    @Test
    fun `does not open a result when Auto-launch is off`() {
        val inputResults: List<SearchResult> = listOf(createLaunchableResult(createRailLaunchable("Alpha")))

        val actualResult: SearchResult.LaunchableResult? = inputResults.autoLaunchResult(settings = Settings())

        assertNull(actualResult)
    }
}

private data class AutoLaunchCase(
    val description: String,
    val results: List<SearchResult>,
    val expectedResult: SearchResult.LaunchableResult?
)
