package core.data.shortcuts

import android.content.pm.LauncherApps
import android.os.UserHandle
import kotlinx.coroutines.flow.Flow

internal const val SHORTCUT_QUERY_FLAGS: Int =
    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC

/** What an app's shortcuts currently look like. */
sealed interface ShortcutsResult {
    /** The app's enabled shortcuts in display order. */
    data class Available(val shortcuts: List<ShortcutLaunchable>) : ShortcutsResult

    /** Rail is not the default launcher, so the system does not expose shortcuts. */
    data object Unavailable : ShortcutsResult

    /** The app's work profile is paused. */
    data object ProfilePaused : ShortcutsResult
}

/** A live source of the shortcuts published by an app in one user profile. */
interface IShortcutSource {
    fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult>
}
