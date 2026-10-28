package core.data

/**
 * Applies [orderedKeys] to [items] while keeping the entries [isPinned] selects, and entries
 * missing from [orderedKeys], in their current slots.
 */
internal fun <T> List<T>.reorderedPinned(orderedKeys: List<String>, keyOf: (T) -> String, isPinned: (T) -> Boolean): List<T> {
    if (isEmpty()) {
        return this
    }
    val itemsByKey: Map<String, T> = associateBy(keyOf)
    val requestedKeys: Set<String> = orderedKeys.toSet()
    val requestedItems: List<T> = orderedKeys
        .mapNotNull { key -> itemsByKey[key] }
        .filterNot { item -> isPinned(item) }
        .distinctBy(keyOf)
    val movableIterator: Iterator<T> = requestedItems.iterator()
    return map { item ->
        if (isPinned(item) || keyOf(item) !in requestedKeys) item else movableIterator.next()
    }
}
