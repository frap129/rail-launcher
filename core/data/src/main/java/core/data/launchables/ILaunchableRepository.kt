package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Rail groups, the App List groups, and whether the installed apps have loaded. */
data class LaunchableState(val groups: List<LaunchableGroup>, val launchables: List<Launchable>, val isLoaded: Boolean) {
    /** The groups shown in the App List, in Rail order. */
    val listedGroups: List<LaunchableGroup.Listed> = groups.filterIsInstance<LaunchableGroup.Listed>()
}

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
    val launchableState: StateFlow<LaunchableState>
}
