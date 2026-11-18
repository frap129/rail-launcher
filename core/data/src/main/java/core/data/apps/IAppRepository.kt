package core.data.apps

import kotlinx.coroutines.flow.Flow

interface IAppRepository {
    /**
     * The Apps Home resolves its Favorites from, or null when neither the Last-known App list
     * nor the live load is available. The Last-known App list is read off the main thread
     * without delaying the live load; [HomeApps.Live] replaces it once the live load publishes.
     */
    val homeApps: Flow<HomeApps?>
}
