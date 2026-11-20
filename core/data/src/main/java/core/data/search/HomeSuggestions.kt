package core.data.search

import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.launches.LaunchCountRecord
import core.data.popups.PopupItem

/** The Favorites plus Home suggestions Home aims for. */
const val HOME_ROW_TARGET: Int = 8

/** The Launch count a Launchable needs to be a Home suggestion. */
const val HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT: Int = 3

/** How many times the median Favorite's Recent use score the top candidate's must reach to add one row over the target. */
const val HOME_OVER_TARGET_SCORE_FACTOR: Int = 2

/**
 * The Home suggestions shown below [favorites]: Launchables with at least [HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT]
 * launches, highest Recent use score at [nowEpochMs] first, filling Home up to [HOME_ROW_TARGET]
 * rows. One more row is allowed when the top candidate's score is at least [HOME_OVER_TARGET_SCORE_FACTOR]
 * times the median Favorite's, so with no Favorites there is no extra row and a Favorite with no
 * launch record counts as scoring zero. The keys [homeSuggestionExcludedKeys] returns and keys
 * [suggestibleLaunchableFor] rejects are left out. Rail itself and the private Apps of a Locked
 * Private Space never reach the Launchable State, so their keys resolve to no Launchable here.
 */
fun LaunchableState.homeSuggestions(
    favorites: List<Launchable>,
    launchCounts: List<LaunchCountRecord>,
    folderMembers: Map<String, List<PopupItem>>,
    stoppedKeys: Set<String>,
    nowEpochMs: Long
): List<Launchable> {
    val recordsByKey: Map<String, LaunchCountRecord> = launchCounts.associateBy { record -> record.key }
    val excludedKeys: Set<String> =
        homeSuggestionExcludedKeys(favorites = favorites, folderMembers = folderMembers, stoppedKeys = stoppedKeys)
    val candidates: List<Pair<Launchable, Double>> = launchCounts
        .asSequence()
        .filter { record -> record.launchCount >= HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT && record.key !in excludedKeys }
        .mapNotNull { record -> suggestibleLaunchableFor(key = record.key) }
        .map { launchable -> launchable to recordsByKey.getValue(launchable.key).recentUseScoreAt(nowEpochMs) }
        .sortedWith(compareByDescending<Pair<Launchable, Double>> { (_, score) -> score }.thenBy { (launchable, _) -> launchable.name })
        .toList()
    val topScore: Double = candidates.firstOrNull()?.second ?: return emptyList()
    val medianFavoriteScore: Double? = favorites
        .map { favorite -> recordsByKey[favorite.key]?.recentUseScoreAt(nowEpochMs) ?: 0.0 }
        .median()
    val extraRows: Int = if (medianFavoriteScore != null && topScore >= HOME_OVER_TARGET_SCORE_FACTOR * medianFavoriteScore) 1 else 0
    val rows: Int = (HOME_ROW_TARGET - favorites.size).coerceAtLeast(0) + extraRows
    return candidates.take(rows).map { (launchable, _) -> launchable }
}

/**
 * The keys Home never suggests: the [favorites], the Apps of the Folders among them, whose items are
 * in [folderMembers] keyed by the Folder key, and the [stoppedKeys] the user chose Stop suggesting for.
 */
fun homeSuggestionExcludedKeys(
    favorites: List<Launchable>,
    folderMembers: Map<String, List<PopupItem>>,
    stoppedKeys: Set<String>
): Set<String> {
    val memberKeys: List<String> = favorites.flatMap { favorite -> folderMembers[favorite.key].orEmpty() }
        .map { item -> item.launchable.key }
    return favorites.mapTo((memberKeys + stoppedKeys).toMutableSet()) { favorite -> favorite.key }
}

private fun List<Double>.median(): Double? {
    if (isEmpty()) {
        return null
    }
    val sortedScores: List<Double> = sorted()
    val middle: Int = size / 2
    return if (size % 2 == 0) (sortedScores[middle - 1] + sortedScores[middle]) / 2 else sortedScores[middle]
}

/** The Launchable stored under [key] unless it is a Hidden app, Paused or one of Rail's built-in Launchables. */
fun LaunchableState.suggestibleLaunchableFor(key: String): Launchable? =
    visibleLaunchableFor(key = key)?.takeUnless { launchable -> launchable.isPaused || launchable is RailLaunchable }
