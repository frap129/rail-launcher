package core.data.cleanup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Collects [upstream] once in this scope and returns its latest value, so every collector of the
 * result shares that one upstream subscription.
 */
internal fun <T : Any> CoroutineScope.shareLatestValue(upstream: Flow<T>): Flow<T> {
    val latest: MutableStateFlow<T?> = MutableStateFlow(null)
    launch { upstream.collect { value -> latest.value = value } }
    return latest.filterNotNull()
}
