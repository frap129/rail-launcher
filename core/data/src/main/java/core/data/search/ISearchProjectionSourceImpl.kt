package core.data.search

import core.data.prefs.ISearchSettings
import core.data.time.IClockSource
import core.util.RESET_ON_STOP_REPLAY_EXPIRATION_MS
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the Search projection from the repositories. The suggestion sources (history and clock)
 * and the typed sources (Shortcuts and contacts) update independently, so Search suggestions
 * appear as soon as the history is known while typed rows wait for every source.
 */
class ISearchProjectionSourceImpl(
    private val searchHistoryRepository: ISearchHistoryRepository,
    private val clockSource: IClockSource,
    private val shortcutSearchSource: IShortcutSearchSource,
    private val contactSearchSource: IContactSearchSource,
    private val searchSettings: ISearchSettings,
    private val webSuggestionSource: IWebSuggestionSource
) : ISearchProjectionSource {
    override fun projection(scope: CoroutineScope, query: Flow<String>): StateFlow<SearchProjection> = combine(
        suggestionSources,
        typedSources,
        webSuggestions(query = query)
    ) { suggestions, typed, web ->
        SearchProjection(suggestions = suggestions, typed = typed, webSuggestions = web)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = WHILE_SUBSCRIBED_TIMEOUT_MS,
            replayExpirationMillis = RESET_ON_STOP_REPLAY_EXPIRATION_MS
        ),
        initialValue = SearchProjection()
    )

    private val suggestionSources: Flow<SearchSuggestionSources> = combine(
        searchHistoryRepository.history,
        clockSource.readings
    ) { history, reading -> SearchSuggestionSources(history = history, reading = reading) }

    // Null until the Shortcuts and contacts have emitted once, so typed rows wait for both.
    private val typedSources: Flow<SearchTypedSources?> = combine(
        shortcutSearchSource.shortcuts,
        contactSearchSource.contacts,
        searchSettings.hasContactsPermission
    ) { shortcuts, contacts, hasContactsPermission ->
        SearchTypedSources(shortcuts = shortcuts, contacts = contacts, hasContactsPermission = hasContactsPermission)
    }.startingUnloaded()

    // Web suggestions follow the live query; flatMapLatest cancels the previous query's request,
    // and the source debounces the new one. A blank query has none.
    private fun webSuggestions(query: Flow<String>): Flow<WebSuggestions> = query
        .distinctUntilChanged()
        .flatMapLatest { currentQuery ->
            if (currentQuery.isBlank()) {
                flowOf(WebSuggestions(query = currentQuery, suggestions = emptyList()))
            } else {
                webSuggestionSource.suggestions(query = currentQuery, locale = Locale.getDefault())
                    .map { suggestions -> WebSuggestions(query = currentQuery, suggestions = suggestions) }
            }
        }
}

/** These values after an initial null, so a consumer can tell a source that has not emitted yet. */
private fun <T> Flow<T>.startingUnloaded(): Flow<T?> = map<T, T?> { value -> value }.onStart { emit(null) }
