package core.data.search

import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** The Web suggestions the selected Search engine offers while the user types. */
interface IWebSuggestionSource {
    /**
     * The Web suggestions for [query] from the selected Search engine, in the engine's order.
     * Emits an empty list while Live web suggestions is off, [query] is blank, [query] shows a
     * Calculator row in [locale] or a typed URL row, or the engine has no suggestion endpoint.
     * Requests are debounced and go only over https to the selected engine; cancelling the flow
     * cancels an in-flight request.
     */
    fun suggestions(query: String, locale: Locale): Flow<List<String>>
}
