package core.data.launches

import kotlinx.coroutines.flow.Flow

interface ILaunchCountRepository {
    /** The Launch counts and Recent use scores stored for Launchable and Shortcut keys, including Folder Pop-up opens. */
    val counts: Flow<List<LaunchCountRecord>>

    /** Records a launch of the Launchable stored under [key], or a Folder's Pop-up open. */
    suspend fun recordLaunch(key: String)
}
