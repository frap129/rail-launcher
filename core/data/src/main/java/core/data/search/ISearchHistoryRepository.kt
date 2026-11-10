package core.data.search

import kotlinx.coroutines.flow.Flow

interface ISearchHistoryRepository {
    /** The launches recorded from Search, including the ones removed from Search suggestions. */
    val history: Flow<List<SearchHistoryRecord>>

    /** Records a launch of the Launchable stored under [key]. */
    suspend fun recordLaunch(key: String)

    /** Removes the Launchable stored under [key] from Search suggestions. */
    suspend fun removeFromSuggestions(key: String)
}
