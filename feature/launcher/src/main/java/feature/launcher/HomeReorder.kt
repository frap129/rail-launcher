package feature.launcher

import core.ui.model.data.favoriteKey
import core.ui.model.data.isFavoriteKey
import core.ui.model.data.isPlaceKey
import core.ui.model.data.placeKey

/** A reorder accepted by Home, already translated to the affected list's indices. */
internal sealed interface HomeReorder {
    data class Place(val fromIndex: Int, val toIndex: Int) : HomeReorder

    data class Favorite(val fromIndex: Int, val toIndex: Int) : HomeReorder
}

/** Resolves a drag between two Home items, rejecting moves that cross Widget and Favorite boundaries. */
internal fun resolveHomeReorder(fromKey: Any?, toKey: Any?, placeIds: List<Long>, favoriteKeys: List<String>): HomeReorder? {
    if (isPlaceKey(fromKey) && isPlaceKey(toKey)) {
        val fromIndex: Int = placeIds.indexOfFirst { id -> placeKey(id) == fromKey }
        val toIndex: Int = placeIds.indexOfFirst { id -> placeKey(id) == toKey }
        return if (fromIndex >= 0 && toIndex >= 0) HomeReorder.Place(fromIndex = fromIndex, toIndex = toIndex) else null
    }
    if (isFavoriteKey(fromKey) && isFavoriteKey(toKey)) {
        val fromIndex: Int = favoriteKeys.indexOfFirst { key -> favoriteKey(key) == fromKey }
        val toIndex: Int = favoriteKeys.indexOfFirst { key -> favoriteKey(key) == toKey }
        return if (fromIndex >= 0 && toIndex >= 0) HomeReorder.Favorite(fromIndex = fromIndex, toIndex = toIndex) else null
    }
    return null
}
