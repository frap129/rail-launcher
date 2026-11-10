package core.data.prefs

import core.util.searchUrlFor

/**
 * The Search engine the Web search row uses: a built-in engine's URL template, or [Custom] with
 * the stored custom template. Every template puts the encoded query after the host.
 */
enum class SearchEngine(val template: String?) {
    Google("https://www.google.com/search?q=%s"),
    DuckDuckGo("https://duckduckgo.com/?q=%s"),
    Brave("https://search.brave.com/search?q=%s"),
    Kagi("https://kagi.com/search?q=%s"),
    Startpage("https://www.startpage.com/sp/search?query=%s"),
    Bing("https://www.bing.com/search?q=%s"),
    Perplexity("https://www.perplexity.ai/search?q=%s"),
    Custom(null);

    /** The web search URL for [query], or null when [Custom] has no valid [customTemplate]. */
    fun searchUrl(query: String, customTemplate: String?): String? {
        val searchTemplate: String = template ?: customTemplate ?: return null
        return searchUrlFor(template = searchTemplate, query = query)
    }
}
