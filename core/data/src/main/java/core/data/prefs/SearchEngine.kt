package core.data.prefs

import core.util.searchUrlFor

/**
 * The Search engine the Web search row uses: a built-in engine's URL template, or [Custom] with
 * the stored custom template. Every template puts the encoded query after the host.
 * [suggestionTemplate] is the engine's public Web suggestion endpoint, or null when it has none.
 */
enum class SearchEngine(val template: String?, val suggestionTemplate: String?) {
    Google(
        "https://www.google.com/search?q=%s",
        "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"
    ),
    DuckDuckGo("https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
    Brave("https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
    Kagi("https://kagi.com/search?q=%s", "https://kagi.com/api/autosuggest?q=%s"),
    Startpage("https://www.startpage.com/sp/search?query=%s", null),
    Bing("https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
    Perplexity("https://www.perplexity.ai/search?q=%s", null),
    Custom(null, null);

    /** The web search URL for [query], or null when [Custom] has no valid [customTemplate]. */
    fun searchUrl(query: String, customTemplate: String?): String? {
        val searchTemplate: String = template ?: customTemplate ?: return null
        return searchUrlFor(template = searchTemplate, query = query)
    }

    /**
     * The URL that asks this engine for its Web suggestions for [query], or null when it offers
     * none: Startpage and Perplexity have no public suggestion endpoint, and [Custom] uses the
     * stored template for searches only, so no query leaves Rail for another engine.
     */
    fun suggestionUrl(query: String): String? = suggestionTemplate?.let { template -> searchUrlFor(template = template, query = query) }

    /** Whether this engine has a public Web suggestion endpoint. */
    val offersSuggestions: Boolean get() = suggestionTemplate != null
}
