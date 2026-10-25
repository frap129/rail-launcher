package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.toAppListLaunchables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeLaunchableRepository(groups: List<LaunchableGroup>, isLoaded: Boolean = true, folders: List<Launchable> = emptyList()) :
    ILaunchableRepository {
    private var currentGroups: List<LaunchableGroup> = groups
    private var currentFolders: List<Launchable> = folders
    private val storedState: MutableStateFlow<LaunchableState> = MutableStateFlow(
        createLaunchableState(groups = groups, folders = folders, isLoaded = isLoaded)
    )
    override val launchableState: StateFlow<LaunchableState> = storedState
    override val launchables: Flow<List<Launchable>> = storedState.map { state -> state.launchables }

    fun setGroups(groups: List<LaunchableGroup>, folders: List<Launchable> = currentFolders) {
        currentGroups = groups
        currentFolders = folders
        storedState.value = createLaunchableState(groups = groups, folders = folders, isLoaded = true)
    }

    fun setFolders(folders: List<Launchable>) {
        setGroups(groups = currentGroups, folders = folders)
    }

    private fun createLaunchableState(groups: List<LaunchableGroup>, folders: List<Launchable>, isLoaded: Boolean): LaunchableState =
        LaunchableState(
            groups = groups,
            launchables = groups.toAppListLaunchables() + folders,
            isLoaded = isLoaded
        )
}
