package core.data.testing

import core.data.search.IWebSuggestionSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A MockK [IWebSuggestionSource] answering every query with [suggestions], or a query's own flow
 * from [suggestionsByQuery] so a test can hold one query's response back.
 */
fun createWebSuggestionSourceMock(
    suggestions: Flow<List<String>> = MutableStateFlow(emptyList()),
    suggestionsByQuery: Map<String, Flow<List<String>>> = emptyMap()
): IWebSuggestionSource {
    val mockWebSuggestionSource: IWebSuggestionSource = mockk()
    every { mockWebSuggestionSource.suggestions(any()) } answers { suggestionsByQuery[firstArg()] ?: suggestions }
    return mockWebSuggestionSource
}
