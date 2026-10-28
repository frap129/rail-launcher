package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Rail groups, the App List groups, every Launchable, and whether the installed apps have loaded. */
data class LaunchableState(
    val groups: List<LaunchableGroup>,
    val launchables: List<Launchable>,
    val isLoaded: Boolean,
    /**
     * The groups the pickers may list: the App List groups without the Private Space container.
     * rail-25f.4: private Apps join the pickers while Private Space is Unlocked.
     */
    val pickerGroups: List<LaunchableGroup.Listed> =
        groups.filterIsInstance<LaunchableGroup.Listed>().filterNot { group -> group is LaunchableGroup.PrivateSpace }
) {
    /** The groups shown in the App List, in Rail order. */
    val listedGroups: List<LaunchableGroup.Listed> = groups.filterIsInstance<LaunchableGroup.Listed>()
}

interface ILaunchableRepository {
    /** Every Launchable, including apps, Rail entries and Folders, with customizations applied. */
    val launchables: Flow<List<Launchable>>

    /**
     * The shared Launchable State, computed off the main thread. Starts with
     * [LaunchableState.isLoaded] false and keeps its upstream subscription for
     * [WHILE_SUBSCRIBED_TIMEOUT_MS] after the last collector leaves.
     */
    val launchableState: StateFlow<LaunchableState>
}
