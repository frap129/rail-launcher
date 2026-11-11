package core.data.search

import core.data.prefs.IPreferencesRepository
import core.data.prefs.SearchEngine
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber

/** How long a query must stay unchanged before the source asks the engine for its Web suggestions. */
private const val WEB_SUGGESTION_DEBOUNCE_MS: Long = 300L

/** How long a suggestion request may take to connect and to read, in milliseconds. */
private const val WEB_SUGGESTION_TIMEOUT_MS: Int = 5_000

/** The largest suggestion response Rail reads; a longer one is ignored. */
private const val MAX_WEB_SUGGESTION_RESPONSE_CHARS: Int = 64_000

/** The most Web suggestions one response may contribute. */
private const val MAX_WEB_SUGGESTIONS: Int = 8

/** The `Accept` header that asks a suggestion endpoint for JSON. */
private const val JSON_ACCEPT_HEADER: String = "application/json"

/** The query, engine, setting and row visibility one suggestions flow depends on. */
private data class WebSuggestionRequest(val query: String, val engine: SearchEngine, val isEnabled: Boolean, val hasDedicatedRow: Boolean)

/**
 * Reads Web suggestions from the selected Search engine over https, while Live web suggestions
 * is on and the engine has a suggestion endpoint. A query that shows its Calculator or typed URL
 * row is never sent. Requests are debounced; a failure logs and emits no rows, and cancelling the
 * flow cancels an in-flight read. [readUrl] is the network read seam, which tests replace so they
 * never open a connection, and defaults to [readInterruptibly].
 */
class IWebSuggestionSourceImpl(
    private val preferencesRepository: IPreferencesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readUrl: suspend (String) -> String? = { url -> readInterruptibly(url = url, dispatcher = dispatcher) }
) : IWebSuggestionSource {
    // flatMapLatest cancels the request of the previous engine or switch, so an older response
    // never reaches the collector.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun suggestions(query: String, locale: Locale): Flow<List<String>> {
        if (query.isBlank()) {
            return flowOf(emptyList())
        }
        return preferencesRepository.settings
            .map { settings ->
                WebSuggestionRequest(
                    query = query,
                    engine = settings.searchEngine,
                    isEnabled = settings.searchWebSuggestions,
                    hasDedicatedRow = settings.dedicatedSearchRows(query = query, locale = locale).hasAnyRow
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { request ->
                if (request.isEnabled && !request.hasDedicatedRow) requestSuggestions(request) else flowOf(emptyList())
            }
            .catch { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                Timber.e(exception, "Failed to fetch Web suggestions")
                emit(emptyList())
            }
    }

    private fun requestSuggestions(request: WebSuggestionRequest): Flow<List<String>> = flow {
        delay(WEB_SUGGESTION_DEBOUNCE_MS)
        emit(fetchSuggestions(request = request))
    }

    private suspend fun fetchSuggestions(request: WebSuggestionRequest): List<String> {
        val url: String = request.engine.suggestionUrl(query = request.query) ?: return emptyList()
        val response: String = readUrl(url) ?: return emptyList()
        return parseWebSuggestions(response = response)
    }
}

/** Reads [url] on [dispatcher], interrupting the blocking read when its coroutine is cancelled. */
private suspend fun readInterruptibly(url: String, dispatcher: CoroutineDispatcher): String? =
    runInterruptible(dispatcher) { readResponse(url = url) }

private fun readResponse(url: String): String? {
    val connection: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = WEB_SUGGESTION_TIMEOUT_MS
        connection.readTimeout = WEB_SUGGESTION_TIMEOUT_MS
        connection.setRequestProperty("Accept", JSON_ACCEPT_HEADER)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            Timber.w("Web suggestions returned HTTP %d", connection.responseCode)
            return null
        }
        readBounded(stream = connection.inputStream)
    } finally {
        connection.disconnect()
    }
}

/** The body of [stream], or null when it is longer than [MAX_WEB_SUGGESTION_RESPONSE_CHARS]. */
internal fun readBounded(stream: InputStream): String? {
    val buffer: CharArray = CharArray(MAX_WEB_SUGGESTION_RESPONSE_CHARS)
    stream.bufferedReader(Charsets.UTF_8).use { reader ->
        var length: Int = 0
        while (length < buffer.size) {
            val read: Int = reader.read(buffer, length, buffer.size - length)
            if (read < 0) {
                return String(buffer, 0, length)
            }
            length += read
        }
        if (reader.read() >= 0) {
            Timber.w("Ignoring a Web suggestion response longer than %d characters", MAX_WEB_SUGGESTION_RESPONSE_CHARS)
            return null
        }
        return String(buffer, 0, length)
    }
}

/**
 * The suggestions in [response], the `[query, [suggestion, ...], ...]` JSON array the supported
 * engines return: their text trimmed, empty and repeated ones dropped, and at most
 * [MAX_WEB_SUGGESTIONS] kept. A response in any other shape has no suggestions.
 */
internal fun parseWebSuggestions(response: String): List<String> {
    val suggestions: JSONArray = try {
        JSONArray(response).optJSONArray(1) ?: return emptyList()
    } catch (exception: JSONException) {
        Timber.e(exception, "Failed to parse Web suggestions")
        return emptyList()
    }
    return buildList {
        for (index in 0 until suggestions.length()) {
            val suggestion: String = suggestions.opt(index) as? String ?: continue
            val trimmedSuggestion: String = suggestion.trim()
            if (trimmedSuggestion.isNotEmpty()) {
                add(trimmedSuggestion)
            }
        }
    }.distinct().take(MAX_WEB_SUGGESTIONS)
}
