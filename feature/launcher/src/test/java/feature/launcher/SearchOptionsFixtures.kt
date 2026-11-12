package feature.launcher

import core.data.prefs.SearchEngine

internal val inputSearchOptions: SearchOptions = SearchOptions(
    openSearchOnSwipeUp = true,
    searchSuggestions = true,
    searchWebSuggestions = false,
    searchContacts = false,
    searchCalculatorRow = true,
    searchEngine = SearchEngine.None
)

internal val inputSearchOptionsActions: SearchOptionsActions = SearchOptionsActions(
    onOpenSearchOnSwipeUpChange = {},
    onSearchSuggestionsChange = {},
    onSearchWebSuggestionsChange = {},
    onSearchContactsChange = {},
    onSearchCalculatorRowChange = {},
    onContactsPermissionResult = { _, _ -> },
    onRefreshContactsPermission = {},
    onOpenSearchSettings = {}
)
