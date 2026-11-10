package core.data.search

import core.data.apps.model.App
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.util.MILLIS_PER_DAY
import core.util.MILLIS_PER_MINUTE

/** How recently an App must have been installed to be a Search suggestion. */
private const val RECENT_INSTALL_WINDOW_MS: Long = 3L * MILLIS_PER_DAY

/** The most recently installed Apps a Search suggestion list includes. */
private const val RECENT_INSTALL_LIMIT: Int = 5

/** How recently a Launchable was opened from Search to be a Search suggestion. */
private const val LAST_OPENED_WINDOW_MS: Long = 15L * MILLIS_PER_MINUTE

/** The most frequently opened Launchables a Search suggestion list includes, bounding the frequent group. */
private const val FREQUENT_SUGGESTION_LIMIT: Int = 5

/**
 * The Search suggestions shown before typing: up to [RECENT_INSTALL_LIMIT] Apps installed in the
 * last [RECENT_INSTALL_WINDOW_MS], the last Launchable opened from Search within
 * [LAST_OPENED_WINDOW_MS], then up to [FREQUENT_SUGGESTION_LIMIT] Launchables opened most often
 * from Search. Launchables removed from Search suggestions are left out, history keys with no
 * Launchable are skipped, and a Launchable is listed once, under the first group it belongs to.
 */
fun LaunchableState.searchSuggestions(history: List<SearchHistoryRecord>, nowEpochMs: Long): List<Launchable> {
    val removedKeys: Set<String> = history
        .filter { record -> record.isRemoved }
        .mapTo(mutableSetOf()) { record -> record.key }
    val visibleHistory: List<SearchHistoryRecord> = history.filterNot { record -> record.isRemoved }
    val candidates: List<Launchable> = searchCandidates()
    val appListOrder: Map<String, Int> = candidates.withIndex().associate { (index, launchable) -> launchable.key to index }
    val recentlyInstalled: List<Launchable> = candidates
        .filterIsInstance<App>()
        .filter { app -> app.key !in removedKeys && nowEpochMs - app.firstInstallTime <= RECENT_INSTALL_WINDOW_MS }
        .sortedByDescending { app -> app.firstInstallTime }
        .take(RECENT_INSTALL_LIMIT)
    val lastOpened: Launchable? = visibleHistory
        .filter { record -> nowEpochMs - record.lastLaunchedAt <= LAST_OPENED_WINDOW_MS }
        .sortedByDescending { record -> record.lastLaunchedAt }
        .firstNotNullOfOrNull { record -> launchableFor(key = record.key) }
    val mostOpened: List<Launchable> = visibleHistory
        .filter { record -> record.launchCount > 0 }
        .sortedWith(
            compareByDescending<SearchHistoryRecord> { record -> record.launchCount }
                .thenBy { record -> appListOrder[record.key] ?: Int.MAX_VALUE }
        )
        .mapNotNull { record -> launchableFor(key = record.key) }
        .take(FREQUENT_SUGGESTION_LIMIT)
    return (recentlyInstalled + listOfNotNull(lastOpened) + mostOpened).distinctBy { launchable -> launchable.key }
}
