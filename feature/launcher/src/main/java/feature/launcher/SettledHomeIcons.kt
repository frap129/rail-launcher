package feature.launcher

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import core.data.launchables.model.Launchable
import core.ui.model.data.favoriteKey

/**
 * The Favorite icons Home has shown and whether each has drawn or failed to, so Home can tell when
 * every visible one has finished; a Home with no visible Favorite icon has nothing to wait for.
 */
internal class SettledHomeIcons {
    private val settledFavoriteKeys: SnapshotStateSet<String> = mutableStateSetOf()

    /** Records that the Favorite icon of [favorite] has drawn or failed; repeats do nothing. */
    fun settle(favorite: Launchable) {
        settledFavoriteKeys.add(favoriteKey(key = favorite.key))
    }

    /** Whether every Favorite icon in [visibleFavoriteKeys] has drawn or failed. */
    fun hasSettledEvery(visibleFavoriteKeys: Collection<String>): Boolean =
        visibleFavoriteKeys.all { visibleFavoriteKey -> visibleFavoriteKey in settledFavoriteKeys }
}
