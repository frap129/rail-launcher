package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.toPickerGroups
import core.data.profiles.ProfileToken
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Rail groups, the App List groups, every Launchable, and whether the installed apps have loaded. */
data class LaunchableState(
    val groups: List<LaunchableGroup>,
    val launchables: List<Launchable>,
    val isLoaded: Boolean,
    /**
     * The Profile tokens of every Private Space whose Launchable and Shortcut keys Rail keeps:
     * a Locked one, a Lock requested one, or one whose Apps are not enumerated yet. The Apps
     * leave the list while Locked and arrive after Unlocking, so keeping the keys until they
     * do stops the cleanups from treating them as stale. A Private Space that is removed
     * releases its keys.
     */
    val keptKeys: Set<ProfileToken> = emptySet(),
    /**
     * The Profile tokens of every Private Space that is Unlocked with its Apps enumerated,
     * including a Space that listed none. Locked or not yet enumerated Spaces are left out.
     */
    val enumeratedKeys: Set<ProfileToken> = emptySet(),
    /**
     * The groups the pickers may list: the App List groups without the Locked Private Space
     * container, so private Apps join the pickers while the Private Space is Unlocked.
     */
    val pickerGroups: List<LaunchableGroup.Listed> = groups.toPickerGroups()
) {
    private val keyResolver: LaunchableKeyResolver by lazy { LaunchableKeyResolver(launchables = launchables) }

    /** The groups shown in the App List, in Rail order. */
    val listedGroups: List<LaunchableGroup.Listed> = groups.filterIsInstance<LaunchableGroup.Listed>()

    /** The current key of the Launchable [key] refers to, resolving a Legacy App key, or null when it is gone. */
    fun resolveKey(key: String): String? = keyResolver.resolve(key = key)

    /** The Launchable [key] refers to, resolving a Legacy App key, or null when it is gone. */
    fun launchableFor(key: String): Launchable? = resolveKey(key = key)?.let { resolvedKey ->
        launchables.firstOrNull { launchable -> launchable.key == resolvedKey }
    }
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
