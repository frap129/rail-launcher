package core.data.shortcuts

import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.apps.model.App
import kotlinx.coroutines.flow.Flow

internal const val SHORTCUT_QUERY_FLAGS: Int =
    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
internal const val SHORTCUT_RESOLVE_FLAGS: Int = SHORTCUT_QUERY_FLAGS or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

/** What an app's shortcuts currently look like. */
sealed interface ShortcutsResult {
    /** The app's enabled shortcuts in display order. */
    data class Available(val shortcuts: List<ShortcutLaunchable>) : ShortcutsResult

    /** Rail is not the default launcher, so the system does not expose shortcuts. */
    data object Unavailable : ShortcutsResult

    /** The app's work profile is paused. */
    data object ProfilePaused : ShortcutsResult
}

/** Every App and what its shortcuts currently look like. */
data class AppShortcuts(val app: App, val result: ShortcutsResult)

/** A live source of the shortcuts published by an app in one user profile. */
interface IShortcutSource {
    fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult>

    /** Every App in the launchable list with its shortcuts. Only queries while collected. */
    fun appsWithShortcuts(): Flow<List<AppShortcuts>>

    /**
     * The Shortcut for every requested key, or null when it no longer exists.
     * A disabled Shortcut is returned with `isEnabled = false`.
     * Keys whose shortcuts cannot be read are absent from the map.
     */
    fun resolve(keys: Set<ShortcutKey>): Flow<Map<ShortcutKey, ShortcutLaunchable?>>

    /** Pins [ids] for the package in [profile], or unpins every shortcut when [ids] is empty. */
    suspend fun pin(packageName: String, profile: UserHandle, ids: Set<String>)
}
