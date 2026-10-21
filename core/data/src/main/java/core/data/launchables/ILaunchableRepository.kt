package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow

/** The Launchables and whether the installed apps have loaded. */
data class LaunchableState(val groups: List<LaunchableGroup>, val launchables: List<Launchable>, val isLoaded: Boolean)

/** How long the shared Launchable State stays subscribed after the last collector leaves. */
const val SHARED_SUBSCRIPTION_TIMEOUT_MS: Long = 5_000

interface ILaunchableRepository {
    /** Every Launchable, apps and Rail entries, with customizations applied. */
    val launchables: Flow<List<Launchable>>

    /**
     * The shared Launchable State, computed off the main thread. Starts with
     * [LaunchableState.isLoaded] false and keeps its upstream subscription for
     * [SHARED_SUBSCRIPTION_TIMEOUT_MS] after the last collector leaves.
     */
    val launchableState: Flow<LaunchableState>

    /** Rail entries in order: the Star, Initial groups ("#" for digits first), then the Builtin group. */
    val launchableGroups: Flow<List<LaunchableGroup>>
}
