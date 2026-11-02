package core.data.shortcuts

import kotlinx.coroutines.flow.Flow

/** The Standalone shortcuts another app asked Rail to add. */
interface IStandaloneShortcutRepository {
    /** Every stored Standalone shortcut the platform still publishes, in the order it was added. */
    val shortcuts: Flow<List<ShortcutLaunchable>>

    /**
     * Runs [accept] under the Shortcut pin lock, then stores [key] and adds it to the Favorites in
     * one transaction when it succeeds. A failed or thrown [accept] stores and pins nothing.
     *
     * @return whether the shortcut was added; adding an existing key is idempotent.
     */
    suspend fun add(key: ShortcutKey, accept: suspend () -> Boolean): Boolean
}
