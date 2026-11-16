package core.data.launchables

import core.data.launches.LaunchCountRecord
import core.data.prefs.AppListOrder

/** The stored usage the App List ranks by, and whether Folders rank with the other Launchables. */
internal data class AppListUsage(val launchCountsByKey: Map<String, LaunchCountRecord>, val rankFoldersByUse: Boolean)

/** How the App List ranks the Launchables inside a group. */
internal sealed interface AppListRanking {
    /** Keeps the grouped order. */
    data object Alphabetical : AppListRanking

    /** Ranks by the all-time Launch count, highest first. */
    data class MostUsed(val usage: AppListUsage) : AppListRanking

    /**
     * Ranks by [core.data.launches.recentUseRank], highest first: a later update outranks an
     * earlier one at the same score, and the order equals ranking the scores decayed to any
     * common moment.
     */
    data class RecentlyUsed(val usage: AppListUsage) : AppListRanking
}

/** The ranking [this] App List order chooses over [usage]. */
internal fun AppListOrder.toAppListRanking(usage: AppListUsage): AppListRanking = when (this) {
    AppListOrder.ALPHABETICAL -> AppListRanking.Alphabetical
    AppListOrder.MOST_USED -> AppListRanking.MostUsed(usage = usage)
    AppListOrder.RECENTLY_USED -> AppListRanking.RecentlyUsed(usage = usage)
}
