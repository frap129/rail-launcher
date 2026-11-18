package core.data.launchables

/**
 * How far the Launchable State has resolved. [LastKnownHomeReady] resolves only Home's
 * Favorites, Folders and Pop-ups from the Last-known App list; its App List groups and picker
 * groups are empty, so it never drives the App List, Rail groups, stale-key cleanup or App
 * Widget reconciliation. [HomeReady] means the live App load did, so Home resolves its
 * Favorites with Launchable customizations, Folder membership and Private Space rules;
 * [AppListReady] also has the Launch counts that order the App List groups.
 */
enum class LaunchableReadiness {
    Loading,
    LastKnownHomeReady,
    HomeReady,
    AppListReady;

    /** Whether Home's Launchables can be resolved. */
    val isHomeReady: Boolean get() = this != Loading

    /** Whether the App List groups are complete and ordered. */
    val isAppListReady: Boolean get() = this == AppListReady

    /** Whether the Launchables came from the live App load, the only source the App List may show. */
    val isLive: Boolean get() = this == HomeReady || this == AppListReady
}
