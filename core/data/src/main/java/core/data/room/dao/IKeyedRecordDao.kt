package core.data.room.dao

/** A DAO whose table holds one record per key. */
interface IKeyedRecordDao<T> {
    suspend fun get(key: String): T?

    suspend fun upsert(record: T)
}

/**
 * Applies [transform] to the record stored for [key], or to null when there is none, and stores it.
 * The read and the write are separate DAO calls, so callers must run this from a `@Transaction` method to make them atomic.
 */
suspend fun <T> IKeyedRecordDao<T>.updateRecord(key: String, transform: (previous: T?) -> T) {
    upsert(transform(get(key = key)))
}
