package feature.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import core.data.search.SearchResult
import core.util.tryStartActivity

/**
 * Opens [row] outside Rail: a typed URL or web search in the default browser, or the Play Store
 * search with its web address as the fallback. Returns whether an app accepted the intent.
 */
internal fun openSearchResultRow(context: Context, row: SearchResult.ExternalRow): Boolean = when (row) {
    is SearchResult.TypedUrlRow -> openBrowserUrl(context = context, url = row.url)
    is SearchResult.WebSearchRow -> openBrowserUrl(context = context, url = row.url)
    is SearchResult.WebSuggestionRow -> openBrowserUrl(context = context, url = row.url)
    is SearchResult.PlayStoreRow -> openPlayStoreRow(context = context, row = row)
}

private fun openBrowserUrl(context: Context, url: String): Boolean = context.tryStartActivity(intent = browserIntent(url = url))

/** Opens the Play Store search of [row], falling back to its web search when no app accepts the market intent. */
private fun openPlayStoreRow(context: Context, row: SearchResult.PlayStoreRow): Boolean =
    context.tryStartActivity(intent = Intent(Intent.ACTION_VIEW, Uri.parse(row.marketUrl))) ||
        context.tryStartActivity(intent = browserIntent(url = row.webUrl))

/** The intent that opens [url] in the default browser, never in a specific app. */
private fun browserIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
