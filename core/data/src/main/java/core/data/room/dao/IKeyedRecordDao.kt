package core.data.room.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

/** The set of the keys, read with [keyOf], of every record of each emitted list. */
fun <T> Flow<List<T>>.mapToKeySet(keyOf: (T) -> String): Flow<Set<String>> = map { records -> records.mapTo(mutableSetOf(), keyOf) }
