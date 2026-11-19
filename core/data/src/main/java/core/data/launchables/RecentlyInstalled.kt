package core.data.launchables

import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.util.RECENT_INSTALL_WINDOW_MS

/**
 * The Apps among [this] first installed within the recent-install window before [nowEpochMs],
 * newest first. The Recently installed section and the Search suggestions share this one view
 * of a recently installed App.
 */
internal fun List<Launchable>.recentlyInstalledApps(nowEpochMs: Long): List<App> = filterIsInstance<App>()
    .filter { app -> nowEpochMs - app.firstInstallTime <= RECENT_INSTALL_WINDOW_MS }
    .sortedByDescending { app -> app.firstInstallTime }
