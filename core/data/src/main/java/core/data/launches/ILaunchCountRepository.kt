package core.data.launches

import kotlinx.coroutines.flow.Flow

interface ILaunchCountRepository {
    /** The launch counts stored for Launchable and Shortcut keys. */
    val counts: Flow<List<LaunchCountRecord>>

    /** Records a launch of the Launchable stored under [key]. */
    suspend fun recordLaunch(key: String)
}
