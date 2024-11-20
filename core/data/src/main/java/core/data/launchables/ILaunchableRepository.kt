package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow

interface ILaunchableRepository {
    val launchables: Flow<List<Launchable>>
    val launchableGroups: Flow<List<LaunchableGroup>>
}
