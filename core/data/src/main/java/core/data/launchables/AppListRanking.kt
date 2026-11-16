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

    /** Ranks by the Recent use score decayed to [nowEpochMs], highest first. */
    data class RecentlyUsed(val usage: AppListUsage, val nowEpochMs: Long) : AppListRanking
}

/** The ranking [this] App List order chooses over [usage] at [nowEpochMs]. */
internal fun AppListOrder.toAppListRanking(usage: AppListUsage, nowEpochMs: Long): AppListRanking = when (this) {
    AppListOrder.ALPHABETICAL -> AppListRanking.Alphabetical
    AppListOrder.MOST_USED -> AppListRanking.MostUsed(usage = usage)
    AppListOrder.RECENTLY_USED -> AppListRanking.RecentlyUsed(usage = usage, nowEpochMs = nowEpochMs)
}
