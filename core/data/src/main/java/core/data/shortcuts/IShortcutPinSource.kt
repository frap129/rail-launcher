package core.data.shortcuts

import core.data.apps.model.AppProfile

/** The Shortcut ids one source needs the system to keep pinned, grouped by package and Profile. */
fun interface IShortcutPinSource {
    /** The Shortcut ids to keep pinned, by publishing package and Profile. */
    suspend fun pinSets(): Map<AppProfile, Set<String>>
}

/** The Shortcut ids in these stored keys, grouped by publishing package and Profile. */
internal fun Iterable<String>.toShortcutPinSets(): Map<AppProfile, Set<String>> = mapNotNull { key -> ShortcutKey.parse(key) }
    .groupBy({ key -> key.toAppProfile() }, { key -> key.id })
    .mapValues { (_, ids) -> ids.toSet() }
