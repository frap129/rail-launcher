package core.data.search

import core.data.prefs.SearchEngine
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class WebSuggestionTest {
    @Test
    fun `parses the suggestion response of every engine that offers suggestions`() {
        val inputResponses: Map<SearchEngine, Pair<String, List<String>>> = mapOf(
            SearchEngine.Google to (
                """["rail launcher",["rail launcher app","rail launcher android"],[],{"google:suggestsubtypes":[[512],[512]]}]"""
                    to listOf("rail launcher app", "rail launcher android")
                ),
            SearchEngine.DuckDuckGo to (
                """["rail launcher",["rail launcher app","rail launcher linux"]]"""
                    to listOf("rail launcher app", "rail launcher linux")
                ),
            SearchEngine.Brave to (
                """["rail launcher",["rail launcher github","rail launcher apk"]]"""
                    to listOf("rail launcher github", "rail launcher apk")
                ),
            SearchEngine.Kagi to (
                """["rail launcher",["rail launcher app","rail launcher review"]]"""
                    to listOf("rail launcher app", "rail launcher review")
                ),
            SearchEngine.Bing to (
                """["rail launcher",["rail launcher download","rail launcher setup"]]"""
                    to listOf("rail launcher download", "rail launcher setup")
                )
        )

        inputResponses.forEach { (engine, input) ->
            val (inputResponse, expectedSuggestions) = input

            assertEquals(engine.name, expectedSuggestions, parseWebSuggestions(response = inputResponse))
        }
    }

    @Test
    fun `trims blank and repeated suggestions`() {
        val inputResponse: String = """["rail",[" rail ","","railroad"," rail "]]"""

        val actualSuggestions: List<String> = parseWebSuggestions(response = inputResponse)

        assertEquals(listOf("rail", "railroad"), actualSuggestions)
    }

    @Test
    fun `keeps at most eight suggestions`() {
        val inputResponse: String =
            """["rail",["rail 1","rail 2","rail 3","rail 4","rail 5","rail 6","rail 7","rail 8","rail 9","rail 10"]]"""

        val actualSuggestions: List<String> = parseWebSuggestions(response = inputResponse)

        assertEquals(
            listOf("rail 1", "rail 2", "rail 3", "rail 4", "rail 5", "rail 6", "rail 7", "rail 8"),
            actualSuggestions
        )
    }

    @Test
    fun `has no suggestions for a malformed response`() {
        assertTrue(parseWebSuggestions(response = "<html>").isEmpty())
        assertTrue(parseWebSuggestions(response = """{"query":"rail","results":["railway"]}""").isEmpty())
        assertTrue(parseWebSuggestions(response = """["rail"]""").isEmpty())
    }
}
