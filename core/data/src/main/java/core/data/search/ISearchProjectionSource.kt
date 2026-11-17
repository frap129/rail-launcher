package core.data.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The source of the Search projection: every Search source a Search session reads. */
interface ISearchProjectionSource {
    /**
     * The projection Search shows for [query]. Its sources start with the first collector and stop
     * after the shared WhileSubscribed timeout once the last one leaves [scope], so Search reads
     * them only while it is open. A session that starts after that timeout starts unloaded, so no
     * row or Auto-launch is ever built from a previous session's sources.
     */
    fun projection(scope: CoroutineScope, query: Flow<String>): StateFlow<SearchProjection>
}
