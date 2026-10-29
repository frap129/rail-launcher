package core.data.room.dao

/** Copies the row stored under [oldKey] to [newKey], then removes [oldKey]; a no-op when they match. */
internal suspend fun replaceStoredKey(
    oldKey: String,
    newKey: String,
    copy: suspend (String, String) -> Unit,
    delete: suspend (String) -> Unit
) {
    if (oldKey == newKey) {
        return
    }
    copy(oldKey, newKey)
    delete(oldKey)
}
