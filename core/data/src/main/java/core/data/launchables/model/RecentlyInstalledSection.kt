package core.data.launchables.model

import core.data.apps.model.App

/**
 * The Recently installed section of the App List: its Apps first installed within the
 * recent-install window, newest first, each also staying in its own group. It follows the
 * Private Space group and holds no Rail mark of its own, so it is not one of the
 * [LaunchableGroup]s; scrolling to the end of the App List reaches it.
 */
data class RecentlyInstalledSection(val apps: List<App>)
