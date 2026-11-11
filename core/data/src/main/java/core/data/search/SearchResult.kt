package core.data.search

import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.prefs.Settings
import core.util.evaluateCalculatorExpression
import core.util.normalizedForSearch
import core.util.substituteSearchTemplate
import core.util.typedUrl
import java.util.Locale

/** One row of Search results. */
sealed interface SearchResult {
    /** The row's stable identity in the results list. */
    val key: String

    /** A Search result that opens outside Rail, in the default browser or the Play Store. */
    sealed interface ExternalRow : SearchResult

    /** A Launchable or Shortcut result. */
    data class LaunchableResult(val launchable: Launchable) : SearchResult {
        override val key: String = launchable.key
    }

    /** The Calculator row: [value] is the formatted result of [expression]. */
    data class CalculatorRow(val expression: String, val value: String) : SearchResult {
        override val key: String = CALCULATOR_ROW_KEY
    }

    /** The row for a matching [contact], with call and message actions. */
    data class ContactRow(val contact: SearchContact) : SearchResult {
        override val key: String = CONTACT_ROW_KEY_PREFIX + contact.contactId
    }

    /** The row that opens [url] directly because the query looks like a web address. */
    data class TypedUrlRow(val url: String) : ExternalRow {
        override val key: String = TYPED_URL_ROW_KEY
    }

    /** The row that opens the web search at [url] with the Search engine. */
    data class WebSearchRow(val url: String) : ExternalRow {
        override val key: String = WEB_SEARCH_ROW_KEY
    }

    /** The row that opens the web search for [suggestion] with the Search engine. */
    data class WebSuggestionRow(val suggestion: String, val url: String) : ExternalRow {
        override val key: String = WEB_SUGGESTION_ROW_KEY_PREFIX + suggestion
    }

    /** The row that opens the Play Store search at [marketUrl], falling back to [webUrl]. */
    data class PlayStoreRow(val marketUrl: String, val webUrl: String) : ExternalRow {
        override val key: String = PLAY_STORE_ROW_KEY
    }
}

private const val CALCULATOR_ROW_KEY: String = "search-result:calculator"
private const val CONTACT_ROW_KEY_PREFIX: String = "search-result:contact:"
private const val TYPED_URL_ROW_KEY: String = "search-result:typed-url"
private const val WEB_SEARCH_ROW_KEY: String = "search-result:web"
private const val WEB_SUGGESTION_ROW_KEY_PREFIX: String = "search-result:web-suggestion:"
private const val PLAY_STORE_ROW_KEY: String = "search-result:play-store"
private const val PLAY_STORE_MARKET_SEARCH_TEMPLATE: String = "market://search?q=%s"
private const val PLAY_STORE_WEB_SEARCH_TEMPLATE: String = "https://play.google.com/store/search?q=%s&c=apps"

/** These Launchables as Search results, in order. */
fun List<Launchable>.toSearchResults(): List<SearchResult> = map { launchable -> SearchResult.LaunchableResult(launchable) }

/**
 * The Search rows for [query]: the Launchable and Shortcut matches, then the Calculator row when
 * [query] is an arithmetic expression in [locale], the Contact rows matched from [contacts] with
 * the same matcher as Launchables and Shortcuts, the typed-URL row when [query] looks like a web
 * address, the Web search row and the Play Store row. Contact rows stay on device and open other
 * apps, so they follow the Calculator row and precede the rows that leave Rail; all of them
 * always follow the Launchable results in that fixed order. [webSuggestions] become Web suggestion
 * rows after the Play Store row, so they never move a row above them. [settings] turns each row
 * kind on or off; the web switch covers both the typed-URL and Web search rows. A blank query has
 * no rows.
 */
fun LaunchableState.searchResultRows(
    query: String,
    launchCounts: Map<String, Int>,
    shortcuts: List<SearchShortcut>,
    contacts: List<SearchContact>,
    webSuggestions: List<String>,
    locale: Locale,
    settings: Settings
): List<SearchResult> {
    if (query.isBlank()) {
        return emptyList()
    }
    val launchableResults: List<SearchResult> = searchResults(
        query = query,
        launchCounts = launchCounts,
        shortcuts = shortcuts
    ).toSearchResults()
    val calculatorRow: SearchResult.CalculatorRow? = if (settings.searchCalculatorRow) {
        evaluateCalculatorExpression(query = query, locale = locale)
            ?.let { value -> SearchResult.CalculatorRow(expression = query, value = value) }
    } else {
        null
    }
    val typedUrlRow: SearchResult.TypedUrlRow? = if (settings.searchWebRow) {
        typedUrl(query = query)?.let { url -> SearchResult.TypedUrlRow(url = url) }
    } else {
        null
    }
    val webSearchRow: SearchResult.WebSearchRow? = if (settings.searchWebRow) {
        settings.searchEngine.searchUrl(query = query, customTemplate = settings.searchEngineTemplate)
            ?.let { url -> SearchResult.WebSearchRow(url = url) }
    } else {
        null
    }
    val playStoreRow: SearchResult.PlayStoreRow? = if (settings.searchPlayStoreRow) {
        SearchResult.PlayStoreRow(
            marketUrl = substituteSearchTemplate(template = PLAY_STORE_MARKET_SEARCH_TEMPLATE, query = query),
            webUrl = substituteSearchTemplate(template = PLAY_STORE_WEB_SEARCH_TEMPLATE, query = query)
        )
    } else {
        null
    }
    val contactRows: List<SearchResult> = contacts.toContactRows(query = query)
    val webSuggestionRows: List<SearchResult> = webSuggestions.mapNotNull { suggestion ->
        settings.searchEngine.searchUrl(query = suggestion, customTemplate = settings.searchEngineTemplate)
            ?.let { url -> SearchResult.WebSuggestionRow(suggestion = suggestion, url = url) }
    }
    return launchableResults +
        listOfNotNull(calculatorRow) +
        contactRows +
        listOfNotNull(typedUrlRow, webSearchRow, playStoreRow) +
        webSuggestionRows
}

/** A contact matched at [tier], kept with its tier so the rows sort like Launchable results. */
private data class RankedContact(val tier: SearchMatchTier, val contact: SearchContact)

/** These contacts matched against [query] with the Launchable matcher, strongest match first. */
private fun List<SearchContact>.toContactRows(query: String): List<SearchResult> {
    val normalizedQuery: String = query.normalizedForSearch().trim()
    if (normalizedQuery.isEmpty()) {
        return emptyList()
    }
    return mapNotNull { contact ->
        tierFor(normalizedName = contact.normalizedName, query = normalizedQuery)?.let { tier ->
            RankedContact(tier = tier, contact = contact)
        }
    }.sortedWith(
        compareBy<RankedContact> { ranked -> ranked.tier.ordinal }
            .thenBy { ranked -> ranked.contact.normalizedName }
    ).map { ranked -> SearchResult.ContactRow(contact = ranked.contact) }
}
