package core.data.search

import core.data.prefs.Settings
import core.data.testing.TEST_CALCULATOR_QUERY
import core.data.testing.TEST_GOOGLE_SETTINGS
import core.data.testing.TEST_SDK
import core.data.testing.TEST_TYPED_URL_QUERY
import core.data.testing.createPreferencesRepositoryMock
import java.io.ByteArrayInputStream
import java.util.Locale
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
private val SAMPLE_SUGGESTIONS: List<String> = listOf("railway", "railroad")
private val TEST_LIVE_WEB_SUGGESTION_SETTINGS: Settings = TEST_GOOGLE_SETTINGS.copy(searchWebSuggestions = true)

/** A real source that counts the requests its [IWebSuggestionSourceImpl] answers with [SAMPLE_RESPONSE]. */
private class RequestCountingSource(settingsFlow: Flow<Settings>) {
    var requestCount: Int = 0
        private set

    val source: IWebSuggestionSourceImpl = createSource(
        settingsFlow = settingsFlow,
        readUrl = {
            requestCount++
            SAMPLE_RESPONSE
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IWebSuggestionSourceImplTest {
    private val inputLocale: Locale = Locale.US

    @Test
    fun `waits the debounce delay before requesting suggestions`() = runTest {
        val requestCountingSource: RequestCountingSource =
            RequestCountingSource(settingsFlow = MutableStateFlow(TEST_LIVE_WEB_SUGGESTION_SETTINGS))
        val actualSuggestionLists: MutableList<List<String>> = mutableListOf()
        backgroundScope.launch {
            requestCountingSource.source.suggestions(query = "rail", locale = inputLocale)
                .collect { suggestions -> actualSuggestionLists += suggestions }
        }

        advanceTimeBy(299)
        assertEquals(0, requestCountingSource.requestCount)
        assertEquals(emptyList<List<String>>(), actualSuggestionLists)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, requestCountingSource.requestCount)
        assertEquals(listOf(SAMPLE_SUGGESTIONS), actualSuggestionLists)
    }

    @Test
    fun `makes no request while no Search engine is chosen`() = runTest {
        val requestCountingSource: RequestCountingSource =
            RequestCountingSource(settingsFlow = MutableStateFlow(Settings(searchWebSuggestions = true)))

        val actualSuggestions: List<String> =
            requestCountingSource.source.suggestions(query = "rail", locale = inputLocale).first()

        assertEquals(emptyList<String>(), actualSuggestions)
        assertEquals(0, requestCountingSource.requestCount)
    }

    @Test
    fun `makes no request while Live web suggestions is off`() = runTest {
        val requestCountingSource: RequestCountingSource =
            RequestCountingSource(settingsFlow = MutableStateFlow(Settings(searchWebSuggestions = false)))

        val actualSuggestions: List<String> =
            requestCountingSource.source.suggestions(query = "rail", locale = inputLocale).first()

        assertEquals(emptyList<String>(), actualSuggestions)
        assertEquals(0, requestCountingSource.requestCount)
    }

    @Test
    fun `makes no request for a Calculator expression or a typed URL`() = runTest {
        val requestCountingSource: RequestCountingSource =
            RequestCountingSource(settingsFlow = MutableStateFlow(TEST_LIVE_WEB_SUGGESTION_SETTINGS))
        val inputQueries: List<String> = listOf(TEST_CALCULATOR_QUERY, TEST_TYPED_URL_QUERY)

        val actualSuggestionLists: List<List<String>> = inputQueries.map { query ->
            requestCountingSource.source.suggestions(query = query, locale = inputLocale).first()
        }

        assertEquals(listOf(emptyList<String>(), emptyList<String>()), actualSuggestionLists)
        assertEquals(0, requestCountingSource.requestCount)
    }

    @Test
    fun `fetches suggestions while the matching row is off`() = runTest {
        val inputCases: List<Pair<String, Settings>> = listOf(
            TEST_CALCULATOR_QUERY to TEST_GOOGLE_SETTINGS.copy(searchCalculatorRow = false, searchWebSuggestions = true),
            TEST_TYPED_URL_QUERY to TEST_GOOGLE_SETTINGS.copy(searchWebRow = false, searchWebSuggestions = true)
        )
        val inputSettings: MutableStateFlow<Settings> = MutableStateFlow(inputCases.first().second)
        val requestCountingSource: RequestCountingSource = RequestCountingSource(settingsFlow = inputSettings)

        val actualSuggestionLists: List<List<String>> = inputCases.map { (query, settings) ->
            inputSettings.value = settings
            requestCountingSource.source.suggestions(query = query, locale = inputLocale).first()
        }

        assertEquals(listOf(SAMPLE_SUGGESTIONS, SAMPLE_SUGGESTIONS), actualSuggestionLists)
        assertEquals(2, requestCountingSource.requestCount)
    }

    @Test
    fun `never emits a slow response for an older query`() = runTest {
        val inputOlderResponse: CompletableDeferred<String?> = CompletableDeferred()
        val source: IWebSuggestionSourceImpl = createSource(
            settingsFlow = MutableStateFlow(TEST_LIVE_WEB_SUGGESTION_SETTINGS),
            readUrl = { url -> if (url.contains("older")) inputOlderResponse.await() else """["newer",["newer suggestion"]]""" }
        )
        val inputQueries: MutableStateFlow<String> = MutableStateFlow("older")
        val actualSuggestions: MutableList<List<String>> = mutableListOf()
        backgroundScope.launch {
            inputQueries.flatMapLatest { query -> source.suggestions(query = query, locale = inputLocale) }
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

        val actualSuggestions: List<List<String>> = source.suggestions(query = "rail", locale = inputLocale).toList()

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
}

private fun createSource(settingsFlow: Flow<Settings>, readUrl: suspend (String) -> String?): IWebSuggestionSourceImpl =
    IWebSuggestionSourceImpl(
        preferencesRepository = createPreferencesRepositoryMock(settingsFlow = settingsFlow),
        readUrl = readUrl
    )
