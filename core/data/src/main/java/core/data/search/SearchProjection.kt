package core.data.search

import core.data.time.ClockReading

/** The Search history and clock reading Search suggestions are built from. */
data class SearchSuggestionSources(val history: List<SearchHistoryRecord>, val reading: ClockReading)

/** The Shortcuts and contacts typed Search rows are built from. */
data class SearchTypedSources(val shortcuts: List<SearchShortcut>, val contacts: List<SearchContact>, val hasContactsPermission: Boolean)

/**
 * The Search projection: the Search history, clock reading, Shortcuts, contacts and Web
 * suggestions the Search rows and suggestions are built from. A source Search has not read yet is
 * null, so typed rows wait for every one of them.
 */
data class SearchProjection(
    val suggestions: SearchSuggestionSources? = null,
    val typed: SearchTypedSources? = null,
    val webSuggestions: WebSuggestions = WebSuggestions(query = "", suggestions = emptyList())
)

/** The Web suggestions fetched for [query], so a new query never shows an older query's rows. */
data class WebSuggestions(val query: String, val suggestions: List<String>) {
    /** These suggestions when they belong to [query], or none when they belong to an older query. */
    fun forQuery(query: String): List<String> = if (this.query == query) suggestions else emptyList()
}
