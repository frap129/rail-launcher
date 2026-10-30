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

/** A package representative and what its Shortcuts currently look like. */
data class AppShortcuts(val app: App, val result: ShortcutsResult)

/** A live source of the shortcuts published by an app in one user profile. */
interface IShortcutSource {
    fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult>

    /** Every package representative in the launchable list with its Shortcuts. Only queries while collected. */
    fun appsWithShortcuts(): Flow<List<AppShortcuts>>

    /**
     * The Shortcuts this source could account for among [keys].
     *
     * The resolve query matches manifest, dynamic and pinned Shortcuts. A Shortcut that still
     * exists is in [ShortcutResolution.shortcuts] with [ShortcutLaunchable.isEnabled] set
     * accordingly. A disabled Shortcut resolves with `isEnabled = false` only while it is pinned;
     * the platform force-deletes one that is neither pinned nor cached, so Rail relies on its pin
     * succeeding for stored Shortcuts and reapplies those pins on start. A key is in
     * [ShortcutResolution.goneKeys] only when the App still publishes Shortcuts but this query
     * omitted it. Every other key is unresolved — the App publishes no Shortcuts, its profile is
     * paused or the query failed — and must be kept.
     */
    fun resolve(keys: Set<ShortcutKey>): Flow<ShortcutResolution>

    /** Pins [ids] for the package in [profile], or unpins every shortcut when [ids] is empty. */
    suspend fun pin(packageName: String, profile: UserHandle, ids: Set<String>)
}

/** What one [IShortcutSource.resolve] call could establish about the requested Shortcuts. */
data class ShortcutResolution(
    /** Every requested Shortcut the platform returned, including disabled ones. */
    val shortcuts: Map<ShortcutKey, ShortcutLaunchable> = emptyMap(),

    /** Keys a completed query reported gone. Empty when the Shortcuts could not be read. */
    val goneKeys: Set<ShortcutKey> = emptySet()
)
