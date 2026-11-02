package core.data.shortcuts

import core.data.apps.model.AppProfile

/** The Shortcut ids one source needs the system to keep pinned, grouped by package and Profile. */
fun interface IShortcutPinSource {
    /** The Shortcut ids to keep pinned, by publishing package and Profile. */
    suspend fun pinSets(): Map<AppProfile, Set<String>>
}
