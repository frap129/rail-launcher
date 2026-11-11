package core.data.search

import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import core.data.testing.TEST_SDK
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createRailLaunchable
import core.data.testing.createSearchContact
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

        val actualResults: List<SearchResult> = searchRows(state = inputState, query = "99")

        assertEquals(SearchResult.LaunchableResult(inputApp), actualResults.firstOrNull())
    }

    @Test
    fun `appends the Calculator row after the Launchable and Shortcut results`() {
        val inputApp: Launchable = createRailLaunchable("2+2")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.calculator", shortLabel = "2+2")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))
        val inputShortcuts: List<SearchShortcut> = listOf(
            SearchShortcut(shortcut = inputShortcut, publisherName = "Calculator", isHidden = false)
        )

        val actualResults: List<SearchResult> = searchRows(state = inputState, query = "2+2", shortcuts = inputShortcuts)

        val expectedShortcut: ShortcutLaunchable = inputShortcut.copy(publisherName = "Calculator")
        assertEquals(
            listOf(
                SearchResult.LaunchableResult(inputApp),
                SearchResult.LaunchableResult(expectedShortcut)
            ),
            actualResults.take(n = 2)
        )
        assertEquals(SearchResult.CalculatorRow(expression = "2+2", value = "4"), actualResults[2])
    }

    @Test
    fun `places the Calculator, web and Play Store rows in their fixed order after the Launchable results`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "12*7")

        assertEquals(
            listOf(
                SearchResult.CalculatorRow(expression = "12*7", value = "84"),
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=12%2A7"),
                SearchResult.PlayStoreRow(
                    marketUrl = "market://search?q=12%2A7",
                    webUrl = "https://play.google.com/store/search?q=12%2A7&c=apps"
                )
            ),
            actualResults
        )
    }

    @Test
    fun `places the typed-URL row before the web and Play Store rows`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "example.com")

        assertEquals(
            listOf(
                SearchResult.TypedUrlRow(url = "https://example.com"),
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=example.com"),
                SearchResult.PlayStoreRow(
                    marketUrl = "market://search?q=example.com",
                    webUrl = "https://play.google.com/store/search?q=example.com&c=apps"
                )
            ),
            actualResults
        )
    }

    @Test
    fun `adds a typed-URL row only when the query looks like a web address`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "not a web address")

        assertTrue(actualResults.none { result -> result is SearchResult.TypedUrlRow })
    }

    @Test
    fun `encodes the Play Store query in both of its URLs`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "it's a game")

        assertEquals(
            SearchResult.PlayStoreRow(
                marketUrl = "market://search?q=it%27s%20a%20game",
                webUrl = "https://play.google.com/store/search?q=it%27s%20a%20game&c=apps"
            ),
            actualResults.single { result -> result is SearchResult.PlayStoreRow }
        )
    }

    @Test
    fun `uses the selected Search engine for the web row`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "rail launcher",
            settings = Settings(searchEngine = SearchEngine.DuckDuckGo)
        )

        assertEquals(
            SearchResult.WebSearchRow(url = "https://duckduckgo.com/?q=rail%20launcher"),
            actualResults.single { result -> result is SearchResult.WebSearchRow }
        )
    }

    @Test
    fun `uses the custom Search engine template for the web row`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "rail",
            settings = Settings(searchEngine = SearchEngine.Custom, searchEngineTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE)
        )

        assertEquals(
            SearchResult.WebSearchRow(url = "https://search.example.com/?q=rail"),
            actualResults.single { result -> result is SearchResult.WebSearchRow }
        )
    }

    @Test
    fun `adds no web row when the custom Search engine template is invalid`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "rail",
            settings = Settings(searchEngine = SearchEngine.Custom, searchEngineTemplate = "https://search.example.com/")
        )

        assertTrue(actualResults.none { result -> result is SearchResult.WebSearchRow })
    }

    @Test
    fun `makes the Calculator row the Top result when nothing else matches`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "12*7")

        assertEquals(SearchResult.CalculatorRow(expression = "12*7", value = "84"), actualResults.firstOrNull())
    }

    @Test
    fun `makes the typed-URL row the Top result when no Launchable or Calculator row matches`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "example.com",
            settings = Settings(searchCalculatorRow = false)
        )

        assertEquals(SearchResult.TypedUrlRow(url = "https://example.com"), actualResults.firstOrNull())
    }

    @Test
    fun `makes the web row the Top result when no Launchable, Calculator or typed-URL row matches`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "rail launcher")

        assertEquals(
            SearchResult.WebSearchRow(url = "https://www.google.com/search?q=rail%20launcher"),
            actualResults.firstOrNull()
        )
    }

    @Test
    fun `makes the lone Play Store row the Top result`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "12*7",
            settings = Settings(searchCalculatorRow = false, searchWebRow = false)
        )

        assertEquals(
            SearchResult.PlayStoreRow(
                marketUrl = "market://search?q=12%2A7",
                webUrl = "https://play.google.com/store/search?q=12%2A7&c=apps"
            ),
            actualResults.firstOrNull()
        )
    }

    @Test
    fun `adds no Calculator row when its switch is off`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "12*7",
            settings = Settings(searchCalculatorRow = false)
        )

        assertTrue(actualResults.none { result -> result is SearchResult.CalculatorRow })
    }

    @Test
    fun `adds no typed-URL or web row when the web switch is off`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "example.com",
            settings = Settings(searchWebRow = false)
        )

        assertTrue(
            actualResults.none { result -> result is SearchResult.TypedUrlRow || result is SearchResult.WebSearchRow }
        )
    }

    @Test
    fun `adds no Play Store row when its switch is off`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "example.com",
            settings = Settings(searchPlayStoreRow = false)
        )

        assertTrue(actualResults.none { result -> result is SearchResult.PlayStoreRow })
    }

    @Test
    fun `adds no rows for a blank query`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "   ")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `adds no row when the Calculator check rejects the query`() {
        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "1/0",
            settings = Settings(searchWebRow = false, searchPlayStoreRow = false)
        )

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `places the Contact row after the Calculator row and before the web rows`() {
        val inputContact: SearchContact = createSearchContact(name = "12*7")

        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "12*7",
            contacts = listOf(inputContact)
        )

        assertEquals(
            listOf(
                SearchResult.CalculatorRow(expression = "12*7", value = "84"),
                SearchResult.ContactRow(contact = inputContact),
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=12%2A7"),
                SearchResult.PlayStoreRow(
                    marketUrl = "market://search?q=12%2A7",
                    webUrl = "https://play.google.com/store/search?q=12%2A7&c=apps"
                )
            ),
            actualResults
        )
    }

    @Test
    fun `places Contact rows after the Launchable and Shortcut results`() {
        val inputApp: Launchable = createRailLaunchable("Alice")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages", shortLabel = "Alice")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))
        val inputContacts: List<SearchContact> = listOf(createSearchContact())

        val actualResults: List<SearchResult> = searchRows(
            state = inputState,
            query = "al",
            shortcuts = listOf(SearchShortcut(shortcut = inputShortcut, publisherName = "Messages", isHidden = false)),
            contacts = inputContacts
        )

        assertEquals(
            listOf(
                SearchResult.LaunchableResult(inputApp),
                SearchResult.LaunchableResult(inputShortcut.copy(publisherName = "Messages")),
                SearchResult.ContactRow(contact = inputContacts.single()),
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=al"),
                SearchResult.PlayStoreRow(
                    marketUrl = "market://search?q=al",
                    webUrl = "https://play.google.com/store/search?q=al&c=apps"
                )
            ),
            actualResults
        )
    }

    @Test
    fun `matches contacts with the same accent and case folding as Launchables`() {
        val inputContact: SearchContact = createSearchContact(name = "Café")

        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "CAFE",
            contacts = listOf(inputContact)
        )

        assertEquals(SearchResult.ContactRow(contact = inputContact), actualResults.firstOrNull())
    }

    @Test
    fun `matches only contact name starts for a single letter`() {
        val inputNameStart: SearchContact = createSearchContact(name = "Alice")
        val inputInsideWord: SearchContact = createSearchContact(
            contactId = 2L,
            lookupKey = "com.example.band",
            name = "Band"
        )

        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "a",
            contacts = listOf(inputNameStart, inputInsideWord)
        )

        assertEquals(SearchResult.ContactRow(contact = inputNameStart), actualResults.firstOrNull())
        assertTrue(actualResults.none { result -> result is SearchResult.ContactRow && result.contact == inputInsideWord })
    }

    @Test
    fun `keeps a contact without a number as a Contact row`() {
        val inputContact: SearchContact = createSearchContact(phoneNumber = null)

        val actualResults: List<SearchResult> = searchRows(
            state = inputEmptyState,
            query = "alice",
            contacts = listOf(inputContact)
        )

        assertEquals(SearchResult.ContactRow(contact = inputContact), actualResults.firstOrNull())
    }

    @Test
    fun `adds no Contact row when the source supplied no contact`() {
        val actualResults: List<SearchResult> = searchRows(state = inputEmptyState, query = "alice", contacts = emptyList())

        assertTrue(actualResults.none { result -> result is SearchResult.ContactRow })
    }

    private fun searchRows(
        state: LaunchableState,
        query: String,
        shortcuts: List<SearchShortcut> = emptyList(),
        contacts: List<SearchContact> = emptyList(),
        settings: Settings = Settings()
    ): List<SearchResult> = state.searchResultRows(
        query = query,
        launchCounts = emptyMap(),
        shortcuts = shortcuts,
        contacts = contacts,
        locale = inputLocale,
        settings = settings
    )
}
