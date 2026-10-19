package feature.launcher

import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLaunchableRepository(groups: List<LaunchableGroup>) : ILaunchableRepository {
    override val launchableGroups: Flow<List<LaunchableGroup>> = MutableStateFlow(groups)
    override val launchables: Flow<List<Launchable>> = launchableGroups.map { it.flatMap(LaunchableGroup::items) }
}
