package core.data.apps

import core.data.apps.model.App

/**
 * The Apps Home resolves its Favorites from: the Last-known App list until the live
 * LauncherApps load finishes, then that live load.
 *
 * The variant is the only distinction a consumer needs: [LastKnown] exposes only Home's
 * Favorites, Folders and Pop-ups, while [Live] also drives the App List groups, Rail groups,
 * picker groups, stale-key cleanup and App Widget reconciliation.
 */
sealed interface HomeApps {
    val apps: List<App>

    /** The live LauncherApps load. */
    data class Live(override val apps: List<App>) : HomeApps

    /** The Last-known App list stored after the previous live load. */
    data class LastKnown(override val apps: List<App>) : HomeApps
}
