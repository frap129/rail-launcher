package core.data.search

import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.util.evaluateCalculatorExpression
import java.util.Locale

/** One row of Search results. */
sealed interface SearchResult {
    /** The row's stable identity in the results list. */
    val key: String

    /** A Launchable or Shortcut result. */
    data class LaunchableResult(val launchable: Launchable) : SearchResult {
        override val key: String = launchable.key
    }

    /** The Calculator row: [value] is the formatted result of [expression]. */
    data class CalculatorRow(val expression: String, val value: String) : SearchResult {
        override val key: String = CALCULATOR_ROW_KEY
    }
}

private const val CALCULATOR_ROW_KEY: String = "search-result:calculator"

/** These Launchables as Search results, in order. */
fun List<Launchable>.toSearchResults(): List<SearchResult> = map { launchable -> SearchResult.LaunchableResult(launchable) }

/**
 * The Search rows for [query]: the Launchable and Shortcut matches, then the Calculator row when
 * [query] is an arithmetic expression in [locale]. The Calculator row always follows every
 * Launchable result, so it is the Top result only when none match.
 */
fun LaunchableState.searchResultRows(
    query: String,
    launchCounts: Map<String, Int>,
    shortcuts: List<SearchShortcut>,
    locale: Locale
): List<SearchResult> {
    val launchableResults: List<SearchResult> = searchResults(
        query = query,
        launchCounts = launchCounts,
        shortcuts = shortcuts
    ).toSearchResults()
    val calculatorRow: SearchResult.CalculatorRow? = evaluateCalculatorExpression(query = query, locale = locale)
        ?.let { value -> SearchResult.CalculatorRow(expression = query, value = value) }
    return launchableResults + listOfNotNull(calculatorRow)
}
