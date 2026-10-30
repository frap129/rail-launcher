package core.data.launchables

import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.shortcuts.ShortcutKey

/** The package-level key an App was stored under before Apps were keyed per activity. */
internal val App.legacyAppKey: String get() = "${packageName}_$profile"

/** Resolves stored Launchable keys to a current Launchable's key, including Legacy App keys. */
internal class LaunchableKeyResolver(launchables: List<Launchable>) {
    private val exactKeys: Set<String> = launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
    private val legacyKeys: Map<String, String> = launchables
        .filterIsInstance<App>()
        .groupBy { app -> app.legacyAppKey }
        .mapValues { (_, apps) ->
            (apps.firstOrNull { app -> app.isPackageRepresentative } ?: apps.minBy { app -> app.componentName.className }).key
        }

    /** The key of the Launchable [key] refers to, or null when it is gone. */
    fun resolve(key: String): String? = if (key in exactKeys) key else legacyKeys[key]

    /** The canonical key every Legacy App key among [keys] maps to. */
    fun legacyReplacements(keys: Iterable<String>): Map<String, String> = buildMap {
        for (key: String in keys) {
            val canonicalKey: String = legacyKeys[key]?.takeIf { canonical -> canonical != key } ?: continue
            put(key, canonicalKey)
        }
    }
}

/** The stored keys this Launchable may appear under, including a package representative's Legacy App key. */
internal fun Launchable.storedKeys(): List<String> = when (this) {
    is App -> listOfNotNull(key, legacyAppKey.takeIf { isPackageRepresentative })
    else -> listOf(key)
}

/** The stored keys [key]'s Launchable may appear under, including a package representative's Legacy App key. */
internal fun LaunchableState.storedKeys(key: String): List<String> = launchableFor(key = key)?.storedKeys() ?: listOf(key)

/** The Profile token of a stored Launchable or Shortcut key, or null when [key] carries none. */
internal fun String.profileToken(): ProfileToken? {
    val shortcut: ShortcutKey? = ShortcutKey.parse(this)
    if (shortcut != null) {
        return shortcut.profile.toProfileToken()
    }
    if (ShortcutKey.hasPrefix(this)) {
        return null
    }
    val token: String = substringAfterLast('_')
    return token.takeIf { value -> ShortcutKey.isProfileToken(value) }?.let { value -> ProfileToken(value) }
}

/**
 * Whether [key] belongs to a Profile whose keys Rail keeps: a Locked Profile, or a Private Space
 * whose Apps are not listed yet.
 */
fun LaunchableState.isKeptKey(key: String): Boolean = key.profileToken() in keptKeys

/** The keys among [keys] that neither a listed Launchable nor a Profile whose keys Rail keeps still needs. */
fun LaunchableState.staleKeys(keys: Iterable<String>): List<String> = keys.filterNot { key -> resolveKey(key) != null || isKeptKey(key) }
