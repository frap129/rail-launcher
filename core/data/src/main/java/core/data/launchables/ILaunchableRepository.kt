package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow

interface ILaunchableRepository {
    val launchables: Flow<List<Launchable>>

    /** Rail entries in order: the Star, Initial groups ("#" for digits first), then the Builtin group. */
    val launchableGroups: Flow<List<LaunchableGroup>>
}
