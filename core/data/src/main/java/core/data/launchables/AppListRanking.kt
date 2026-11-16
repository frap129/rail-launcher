package core.data.launchables

import core.data.launches.LaunchCountRecord
import core.data.prefs.AppListOrder

/** How the App List ranks the Launchables inside a group, with the stored usage it reads. */
internal sealed interface AppListRanking {
    /** Keeps the grouped order. */
    data object Alphabetical : AppListRanking

    /** Ranks by the all-time Launch count, highest first. */
    data class MostUsed(val launchCountsByKey: Map<String, LaunchCountRecord>) : AppListRanking

    /** Ranks by the Recent use score decayed to [nowEpochMs], highest first. */
    data class RecentlyUsed(val launchCountsByKey: Map<String, LaunchCountRecord>, val nowEpochMs: Long) : AppListRanking
}

/** The ranking [this] App List order chooses over [launchCountsByKey] at [nowEpochMs]. */
internal fun AppListOrder.toAppListRanking(launchCountsByKey: Map<String, LaunchCountRecord>, nowEpochMs: Long): AppListRanking =
    when (this) {
        AppListOrder.ALPHABETICAL -> AppListRanking.Alphabetical
        AppListOrder.MOST_USED -> AppListRanking.MostUsed(launchCountsByKey = launchCountsByKey)
        AppListOrder.RECENTLY_USED -> AppListRanking.RecentlyUsed(launchCountsByKey = launchCountsByKey, nowEpochMs = nowEpochMs)
    }
