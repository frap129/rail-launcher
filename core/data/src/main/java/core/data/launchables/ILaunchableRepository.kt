package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow

interface ILaunchableRepository {
    val launchables: Flow<List<Launchable>>

    /** Rail entries in order: the Star, Letter groups, then the Builtin group. */
    val launchableGroups: Flow<List<LaunchableGroup>>
}
