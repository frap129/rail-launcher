package core.data.launchables

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.toPickerGroups
import core.data.profiles.ProfileToken
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every group in Rail order with Hidden apps included, every Launchable, how far its
 * resolution has got, and the keys the cleanups keep; [listedGroups] and [groupsFor] leave
 * Hidden apps out unless a caller asks to include them.
 */
data class LaunchableState(
    val groups: List<LaunchableGroup>,
    val launchables: List<Launchable>,
    /** How far the Launchables have resolved: no Apps yet, Last-known or live Home-ready, or App List-ready. */
    val readiness: LaunchableReadiness,
    /**
     * The Profile tokens of every Private Space whose Launchable and Shortcut keys Rail keeps:
     * a Locked one, a Lock requested one, or one whose Apps are not listed yet. The Apps
     * leave the list while Locked and arrive after Unlocking, so keeping the keys until they
     * do stops the cleanups from treating them as stale. A Private Space that is removed
     * releases its keys.
     */
    val keptKeys: Set<ProfileToken> = emptySet(),
    /**
     * Stored keys that are neither resolved nor reported gone, so the stale-key cleanup keeps
     * their Favorites, name customizations and other references until they resolve again.
     */
    val retainedKeys: Set<String> = emptySet(),
    /** The keys of the Hidden apps: Apps and Standalone shortcuts the user hid from the App List. */
    val hiddenKeys: Set<String> = emptySet(),
    /**
     * The groups the pickers may list: the groups without the Locked Private Space container,
     * so private Apps join the pickers while the Private Space is Unlocked. Hidden apps stay listed.
     */
    val pickerGroups: List<LaunchableGroup.Listed> = groups.toPickerGroups()
) {
    /** Whether Home's Launchables can be resolved. */
    val isHomeReady: Boolean get() = readiness.isHomeReady

    /** Whether the App List groups are complete and ordered. */
    val isAppListReady: Boolean get() = readiness.isAppListReady

    private val launchablesByKey: Map<String, Launchable> by lazy { launchables.associateBy { launchable -> launchable.key } }
    private val groupsWithoutHidden: List<LaunchableGroup> by lazy {
        groups.withoutHiddenApps(isHidden = { launchable -> isHidden(launchable) })
    }

    /** The groups shown in the App List, in Rail order. */
    val listedGroups: List<LaunchableGroup.Listed> by lazy { listedGroupsFor(includeHidden = false) }

    /**
     * The Rail and App List groups, including the Hidden apps in their usual groups when
     * [includeHidden] is true, so a caller can list them where they are needed.
     */
    fun groupsFor(includeHidden: Boolean): List<LaunchableGroup> = if (includeHidden) groups else groupsWithoutHidden

    /** The [groupsFor] projection holding only the listed groups, in Rail order. */
    fun listedGroupsFor(includeHidden: Boolean): List<LaunchableGroup.Listed> =
        groupsFor(includeHidden = includeHidden).filterIsInstance<LaunchableGroup.Listed>()

    /** Whether the user hid [launchable] from the App List. */
    fun isHidden(launchable: Launchable): Boolean = launchable.key in hiddenKeys

    /**
     * The Hidden apps, by resolved name. A Locked Private Space leaves its private Apps out,
     * because resolution already does.
     */
    val hiddenApps: List<Launchable> by lazy { launchables.filter { launchable -> isHidden(launchable) }.sortedWith(launchableNameOrder) }

    /** The Launchable stored under [key], or null when it is gone. */
    fun launchableFor(key: String): Launchable? = launchablesByKey[key]

    /** The Launchable stored under [key] unless it is a Hidden app, or null when it is gone. */
    fun visibleLaunchableFor(key: String): Launchable? = launchableFor(key = key)?.takeUnless { launchable -> isHidden(launchable) }
}

/** Orders Launchables by their resolved names, ignoring case. */
internal val launchableNameOrder: Comparator<Launchable> = compareBy(String.CASE_INSENSITIVE_ORDER) { launchable -> launchable.name }

/** A copy of [this] leaving out the Hidden apps, dropping an Initial group left empty by them. */
private fun List<LaunchableGroup>.withoutHiddenApps(isHidden: (Launchable) -> Boolean): List<LaunchableGroup> = mapNotNull { group ->
    when (group) {
        LaunchableGroup.Star -> group
        is LaunchableGroup.InitialGroup -> group.withItems(items = group.itemsWithoutHidden(isHidden)).takeIf { it.items.isNotEmpty() }
        is LaunchableGroup.Listed -> group.withItems(items = group.itemsWithoutHidden(isHidden))
    }
}

private fun LaunchableGroup.Listed.itemsWithoutHidden(isHidden: (Launchable) -> Boolean): List<Launchable> =
    items.filterNot { launchable -> isHidden(launchable) }

interface ILaunchableRepository {
    /** Every Launchable, including apps, Rail entries and Folders, with customizations applied. */
    val launchables: Flow<List<Launchable>>

    /**
     * The shared Launchable State, computed off the main thread. Starts
     * [LaunchableReadiness.Loading], becomes [LaunchableReadiness.LastKnownHomeReady] once the
     * Last-known App list resolves and [LaunchableReadiness.HomeReady] once the live installed
     * Apps have resolved, then [LaunchableReadiness.AppListReady] once the Launch counts that
     * order its groups arrive. Keeps its upstream subscription for
     * [WHILE_SUBSCRIBED_TIMEOUT_MS] after the last collector leaves.
     */
    val launchableState: StateFlow<LaunchableState>
}
