package core.data.prefs

import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineTest {
    @Test
    fun `builds the custom engine URL from the stored template`() {
        val actualUrl: String? = SearchEngine.Custom.searchUrl(
            query = "a b",
            customTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
        )

        assertEquals("https://search.example.com/?q=a%20b", actualUrl)
    }

    @Test
    fun `has no URL for a custom engine without a valid template`() {
        assertNull(SearchEngine.Custom.searchUrl(query = "a", customTemplate = null))
        assertNull(SearchEngine.Custom.searchUrl(query = "a", customTemplate = "https://search.example.com/"))
    }

    @Test
    fun `has no Search URL without a chosen engine even with a stored template`() {
        assertNull(SearchEngine.None.searchUrl(query = "a", customTemplate = null))
        assertNull(SearchEngine.None.searchUrl(query = "a", customTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE))
    }

    @Test
    fun `lists every built-in engine as a Search engine choice`() {
        val expectedEngines: List<SearchEngine> = listOf(
            SearchEngine.Google,
            SearchEngine.DuckDuckGo,
            SearchEngine.Brave,
            SearchEngine.Kagi,
            SearchEngine.Startpage,
            SearchEngine.Bing,
            SearchEngine.Perplexity
        )

        assertEquals(expectedEngines, SearchEngine.selectable)
    }

    @Test
    fun `builds each Web suggestion endpoint with the encoded query`() {
        val inputUrls: Map<SearchEngine, String> = mapOf(
            SearchEngine.Google to "https://suggestqueries.google.com/complete/search?client=firefox&q=rail%20launcher",
            SearchEngine.DuckDuckGo to "https://duckduckgo.com/ac/?q=rail%20launcher&type=list",
            SearchEngine.Brave to "https://search.brave.com/api/suggest?q=rail%20launcher",
            SearchEngine.Kagi to "https://kagi.com/api/autosuggest?q=rail%20launcher",
            SearchEngine.Bing to "https://api.bing.com/osjson.aspx?query=rail%20launcher"
        )

        inputUrls.forEach { (engine, expectedUrl) ->
            assertEquals(expectedUrl, engine.suggestionUrl(query = "rail launcher"))
        }
    }

    @Test
    fun `has no Web suggestion URL for engines without an endpoint`() {
        assertNull(SearchEngine.None.suggestionUrl(query = "rail"))
        assertNull(SearchEngine.Startpage.suggestionUrl(query = "rail"))
        assertNull(SearchEngine.Perplexity.suggestionUrl(query = "rail"))
        assertNull(SearchEngine.Custom.suggestionUrl(query = "rail"))
    }

    @Test
    fun `offers Web suggestions only for engines with an endpoint`() {
        val inputExpected: Map<SearchEngine, Boolean> = mapOf(
            SearchEngine.Google to true,
            SearchEngine.DuckDuckGo to true,
            SearchEngine.Brave to true,
            SearchEngine.Kagi to true,
            SearchEngine.Startpage to false,
            SearchEngine.Bing to true,
            SearchEngine.Perplexity to false,
            SearchEngine.Custom to false,
            SearchEngine.None to false
        )

        inputExpected.forEach { (engine, expectedOffersSuggestions) ->
            assertEquals(expectedOffersSuggestions, engine.offersSuggestions)
        }
    }
}
