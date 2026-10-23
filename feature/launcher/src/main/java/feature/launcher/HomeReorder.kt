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

/** A Widget Area row reported by the reorderable library, with its LazyColumn position. */
internal data class ReorderRow(val listIndex: Int, val key: Any?)

/** Resolves the places a capped Widget Area drag moved between from the rows it straddled. */
internal fun resolvePlaceReorder(rows: List<ReorderRow>, fromListIndex: Int, toListIndex: Int, placeIds: List<Long>): HomeReorder.Place? {
    val fromKey: Any? = rows.firstOrNull { row -> row.listIndex == fromListIndex }?.key
    val toKey: Any? = rows.firstOrNull { row -> row.listIndex == toListIndex }?.key
    val indices: Pair<Int, Int> = resolveMoveIndices(fromKey, toKey, placeIds, ::isPlaceKey, ::placeKey) ?: return null
    return HomeReorder.Place(fromIndex = indices.first, toIndex = indices.second)
}

/** Resolves a drag between two Home items, rejecting moves that cross Widget and Favorite boundaries. */
internal fun resolveHomeReorder(fromKey: Any?, toKey: Any?, placeIds: List<Long>, favoriteKeys: List<String>): HomeReorder? {
    resolveMoveIndices(fromKey, toKey, placeIds, ::isPlaceKey, ::placeKey)?.let { indices ->
        return HomeReorder.Place(fromIndex = indices.first, toIndex = indices.second)
    }
    resolveMoveIndices(fromKey, toKey, favoriteKeys, ::isFavoriteKey, ::favoriteKey)?.let { indices ->
        return HomeReorder.Favorite(fromIndex = indices.first, toIndex = indices.second)
    }
    return null
}

private fun <T> resolveMoveIndices(
    fromKey: Any?,
    toKey: Any?,
    items: List<T>,
    isReorderKey: (Any?) -> Boolean,
    keyOf: (T) -> Any?
): Pair<Int, Int>? {
    if (!isReorderKey(fromKey) || !isReorderKey(toKey)) {
        return null
    }
    val fromIndex: Int = items.indexOfFirst { item -> keyOf(item) == fromKey }
    val toIndex: Int = items.indexOfFirst { item -> keyOf(item) == toKey }
    return if (fromIndex >= 0 && toIndex >= 0) fromIndex to toIndex else null
}
