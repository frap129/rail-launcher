package core.data.room.dao

import androidx.room.Transaction
import core.data.room.DatabaseTransaction
import kotlinx.coroutines.withContext

/** Base for DAOs whose multi-step changes must run in one transaction. */
interface TransactionalDao {
    /**
     * Runs [block] in one database transaction so a multi-step change cannot be half-applied.
     * The transaction is marked in the coroutine context so writes that take a repository lock can
     * skip it: waiting for a lock while holding the database's write lock would invert the locks.
     */
    @Transaction
    suspend fun <T> inTransaction(block: suspend () -> T): T = withContext(DatabaseTransaction) { block() }
}
