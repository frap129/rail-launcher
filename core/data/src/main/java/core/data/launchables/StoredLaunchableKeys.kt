package core.data.launchables

import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.shortcuts.ShortcutKey

/** The keys among [keys] that neither a listed Launchable nor a Profile whose keys Rail keeps still needs. */
fun LaunchableState.staleKeys(keys: Iterable<String>): List<String> = keys.filterNot { key -> launchableFor(key) != null || isKeptKey(key) }

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
