package core.data.room.dao

/** A DAO whose table holds one settings row with id 0. */
interface ISingleRowDao<T> {
    suspend fun getRow(): T?

    suspend fun upsert(row: T)
}

/** Applies [transform] to the stored row, or to [defaultRow] when the table is empty, and stores it. */
suspend fun <T> ISingleRowDao<T>.updateRow(defaultRow: T, transform: (T) -> T) {
    upsert(transform(getRow() ?: defaultRow))
}
