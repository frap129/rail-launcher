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
}
