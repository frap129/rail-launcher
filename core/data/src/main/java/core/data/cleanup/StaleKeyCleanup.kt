package core.data.cleanup

import core.data.retryWithExponentialBackoff
import core.data.runCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One kind of stored row that refers to Launchable keys by key. */
interface IStaleKeyRow {
    /** The keys every stored row of this kind refers to. */
    val keys: Flow<Set<String>>

    /** Removes the stored rows that refer to [keys]. */
    suspend fun delete(keys: Set<String>)

    /** Whether [state] leaves a stored row of this kind referring to a key Rail cannot use. */
    fun isStale(state: StoredKeyState): Boolean = state is StoredKeyState.Stale
}

/** Runs the one stale-key cleanup job over the registered [IStaleKeyRow] kinds. */
interface IStaleKeyCleanup {
    /** Adds [row] to the cleanup, which removes its stale keys once the Launchables load. */
    fun register(row: IStaleKeyRow)
}

/** Classifies every registered row's keys together and deletes the ones the classifier reports stale. */
@OptIn(ExperimentalCoroutinesApi::class)
class IStaleKeyCleanupImpl(private val classifier: IStoredKeyClassifier, scope: CoroutineScope) : IStaleKeyCleanup {
    private val rows: MutableStateFlow<List<IStaleKeyRow>> = MutableStateFlow(emptyList())

    init {
        scope.launch {
            rows.flatMapLatest { currentRows ->
                if (currentRows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    flow {
                        coroutineScope {
                            val rowKeys: Flow<List<Pair<IStaleKeyRow, Set<String>>>> = shareLatestValue(
                                upstream = combine(currentRows.map { row -> row.keys }) { keySets -> currentRows.zip(keySets) }
                                    .distinctUntilChanged()
                            )
                            emitAll(
                                combine(
                                    rowKeys,
                                    classifier.observe(
                                        rowKeys.map { pairs -> pairs.flatMapTo(mutableSetOf()) { (_, keys) -> keys } }
                                    )
                                ) { currentRowKeys, states ->
                                    currentRowKeys.map { (row, keys) ->
                                        RowStaleKeys(row = row, staleKeys = states.staleKeysIn(row = row, keys = keys))
                                    }
                                }
                            )
                        }
                    }
                }
            }
                .retryWithExponentialBackoff()
                .collect { rowStaleKeys -> deleteStaleKeys(rowStaleKeys) }
        }
    }

    override fun register(row: IStaleKeyRow) {
        rows.update { current -> current + row }
    }

    private suspend fun deleteStaleKeys(rowStaleKeys: List<RowStaleKeys>) {
        for ((row: IStaleKeyRow, staleKeys: Set<String>) in rowStaleKeys) {
            if (staleKeys.isEmpty()) {
                continue
            }
            runCleanup(message = "Failed to remove ${staleKeys.size} stale stored keys") {
                row.delete(staleKeys)
            }
        }
    }
}

private data class RowStaleKeys(val row: IStaleKeyRow, val staleKeys: Set<String>)

private fun Map<String, StoredKeyState>?.staleKeysIn(row: IStaleKeyRow, keys: Set<String>): Set<String> =
    orEmpty().filter { (key, state) -> key in keys && row.isStale(state) }.keys
