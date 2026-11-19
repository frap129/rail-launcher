package core.data.search

import kotlinx.coroutines.flow.Flow

interface IStoppedSuggestionRepository {
    /** The keys of the Launchables the user stopped suggesting on Home. */
    val keys: Flow<Set<String>>

    /** Stops suggesting the Launchable stored under [key] on Home. */
    suspend fun stop(key: String)
}
