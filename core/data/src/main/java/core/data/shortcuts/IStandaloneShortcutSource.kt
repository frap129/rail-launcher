package core.data.shortcuts

import kotlinx.coroutines.flow.Flow

/** The stored Standalone shortcuts and the keys the platform could not currently resolve. */
data class StandaloneShortcuts(
    /** Every stored Standalone shortcut the platform still publishes, in the order it was added. */
    val launchables: List<ShortcutLaunchable>,

    /**
     * The stored keys that are neither resolved nor reported gone, such as keys whose Profile is
     * paused or whose query failed. Stale-key cleanup keeps them and their references.
     */
    val unresolvedKeys: Set<String>
)

/** The stored Standalone shortcuts, resolved to the platform's current Shortcut data. */
interface IStandaloneShortcutSource {
    val shortcuts: Flow<StandaloneShortcuts>
}
