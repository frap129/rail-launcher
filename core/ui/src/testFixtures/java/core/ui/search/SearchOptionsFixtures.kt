package core.ui.search

import core.data.prefs.SearchEngine
import core.ui.R

/** The labels of every row in the Search options sheet. */
val SHEET_ROW_LABELS: List<Int> = listOf(
    R.string.search_web_suggestions,
    R.string.search_contacts,
    R.string.search_suggestions,
    R.string.search_calculator_row,
    R.string.search_swipe_up,
    R.string.search_settings
)

val inputSearchOptions: SearchOptions = SearchOptions(
    openSearchOnSwipeUp = true,
    searchSuggestions = true,
    searchWebSuggestions = false,
    searchContacts = false,
    searchCalculatorRow = true,
    searchEngine = SearchEngine.None
)

val inputSearchOptionsActions: SearchOptionsActions = SearchOptionsActions(
    onOpenSearchOnSwipeUpChange = {},
    onSearchSuggestionsChange = {},
    onSearchWebSuggestionsChange = {},
    onSearchContactsChange = {},
    onSearchCalculatorRowChange = {},
    onContactsPermissionResult = { _, _ -> },
    onRefreshContactsPermission = {},
    onOpenSearchSettings = {}
)
