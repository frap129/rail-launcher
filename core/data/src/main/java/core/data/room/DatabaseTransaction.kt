package core.data.room

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Marks a coroutine that runs inside one database transaction, where the database serializes writes. */
internal object DatabaseTransaction : AbstractCoroutineContextElement(Key) {
    /** The context key of [DatabaseTransaction]. */
    object Key : CoroutineContext.Key<DatabaseTransaction>
}
