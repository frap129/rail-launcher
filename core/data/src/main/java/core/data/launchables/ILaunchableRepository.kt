package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow

/** The Launchables and whether the installed apps have loaded. */
data class LaunchableState(val groups: List<LaunchableGroup>, val launchables: List<Launchable>, val isLoaded: Boolean)

interface ILaunchableRepository {
    /** Every Launchable, apps and Rail entries, with customizations applied. */
    val launchables: Flow<List<Launchable>>

    /** The Launchables paired with whether the installed apps have loaded. */
    val launchableState: Flow<LaunchableState>

    /** Rail entries in order: the Star, Initial groups ("#" for digits first), then the Builtin group. */
    val launchableGroups: Flow<List<LaunchableGroup>>
}
