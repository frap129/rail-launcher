package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLaunchableRepository(groups: List<LaunchableGroup>, isLoaded: Boolean = true) : ILaunchableRepository {
    private val storedState: MutableStateFlow<LaunchableState> = MutableStateFlow(
        createLaunchableState(groups = groups, isLoaded = isLoaded)
    )
    override val launchableState: Flow<LaunchableState> = storedState
    override val launchableGroups: Flow<List<LaunchableGroup>> = storedState.map { state -> state.groups }
    override val launchables: Flow<List<Launchable>> = storedState.map { state -> state.launchables }

    fun setGroups(groups: List<LaunchableGroup>) {
        storedState.value = createLaunchableState(groups = groups, isLoaded = true)
    }

    private fun createLaunchableState(groups: List<LaunchableGroup>, isLoaded: Boolean): LaunchableState = LaunchableState(
        groups = groups,
        launchables = groups.filterIsInstance<LaunchableGroup.Listed>().flatMap { group -> group.items },
        isLoaded = isLoaded
    )
}
