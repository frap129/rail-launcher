package core.data.launchables

/**
 * How far the Launchable State has resolved. [HomeReady] means the installed Apps have loaded,
 * so Home resolves its Favorites with Launchable customizations, Folder membership and Private
 * Space rules; [AppListReady] also has the Launch counts that order the App List groups.
 */
enum class LaunchableReadiness {
    Loading,
    HomeReady,
    AppListReady;

    /** Whether Home's Launchables can be resolved. */
    val isHomeReady: Boolean get() = this != Loading

    /** Whether the App List groups are complete and ordered. */
    val isAppListReady: Boolean get() = this == AppListReady
}
