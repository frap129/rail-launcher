package core.data.search

import core.data.prefs.Settings
import core.data.testing.TEST_GOOGLE_SETTINGS
import core.data.testing.TEST_SDK
import core.data.testing.createPreferencesRepositoryMock
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val RESPONSE_LIMIT_CHARS: Int = 64_000
private const val SAMPLE_RESPONSE: String = """["rail",["railway","railroad"]]"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IWebSuggestionSourceImplTest {
    @Test
    fun `waits the debounce delay before requesting suggestions`() = runTest {
        var actualRequestCount: Int = 0
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = MutableStateFlow(TEST_GOOGLE_SETTINGS.copy(searchWebSuggestions = true)),
            readUrl = {
                actualRequestCount++
                SAMPLE_RESPONSE
            }
        )
        backgroundScope.launch { source.suggestions(query = "rail").collect { } }

        advanceTimeBy(299)
        assertEquals(0, actualRequestCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, actualRequestCount)
    }

    @Test
    fun `makes no request while no Search engine is chosen`() = runTest {
        var actualRequestCount: Int = 0
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = MutableStateFlow(Settings(searchWebSuggestions = true)),
            readUrl = {
                actualRequestCount++
                SAMPLE_RESPONSE
            }
        )

        val actualSuggestions: List<String> = source.suggestions(query = "rail").first()

        assertEquals(emptyList<String>(), actualSuggestions)
        assertEquals(0, actualRequestCount)
    }

    @Test
    fun `makes no request while Live web suggestions is off`() = runTest {
        var actualRequestCount: Int = 0
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = MutableStateFlow(Settings(searchWebSuggestions = false)),
            readUrl = {
                actualRequestCount++
                SAMPLE_RESPONSE
            }
        )

        val actualSuggestions: List<String> = source.suggestions(query = "rail").first()

        assertEquals(emptyList<String>(), actualSuggestions)
        assertEquals(0, actualRequestCount)
    }

    @Test
    fun `never emits a slow response for an older query`() = runTest {
        val inputOlderResponse: CompletableDeferred<String?> = CompletableDeferred()
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = MutableStateFlow(TEST_GOOGLE_SETTINGS.copy(searchWebSuggestions = true)),
            readUrl = { url -> if (url.contains("older")) inputOlderResponse.await() else """["newer",["newer suggestion"]]""" }
        )
        val inputQueries: MutableStateFlow<String> = MutableStateFlow("older")
        val actualSuggestions: MutableList<List<String>> = mutableListOf()
        backgroundScope.launch {
            inputQueries.flatMapLatest { query -> source.suggestions(query = query) }
                .collect { suggestions -> actualSuggestions += suggestions }
        }

        advanceTimeBy(300)
        runCurrent()
        inputQueries.value = "newer"
        advanceTimeBy(300)
        runCurrent()
        inputOlderResponse.complete("""["older",["older suggestion"]]""")
        runCurrent()

        assertEquals(listOf(listOf("newer suggestion")), actualSuggestions)
    }

    @Test
    fun `emits no suggestions when the settings flow fails`() = runTest {
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = flow { throw IllegalStateException("boom") },
            readUrl = { SAMPLE_RESPONSE }
        )

        val actualSuggestions: List<List<String>> = source.suggestions(query = "rail").toList()

        assertEquals(listOf(emptyList<String>()), actualSuggestions)
    }

    @Test
    fun `reads a response exactly at the size limit`() {
        val inputResponse: String = "a".repeat(RESPONSE_LIMIT_CHARS)

        val actualResponse: String? = readBounded(stream = ByteArrayInputStream(inputResponse.toByteArray()))

        assertEquals(inputResponse, actualResponse)
    }

    @Test
    fun `ignores a response one character over the size limit`() {
        val inputResponse: String = "a".repeat(RESPONSE_LIMIT_CHARS + 1)

        val actualResponse: String? = readBounded(stream = ByteArrayInputStream(inputResponse.toByteArray()))

        assertNull(actualResponse)
    }

    private fun createSource(settingsFlow: Flow<Settings>, readUrl: suspend (String) -> String?): IWebSuggestionSourceImpl =
        IWebSuggestionSourceImpl(
            preferencesRepository = createPreferencesRepositoryMock(settingsFlow = settingsFlow),
            readUrl = readUrl
        )
}
