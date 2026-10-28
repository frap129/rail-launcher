package core.data.testing

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Fails each collection as soon as [failureRequests] receives, so a collector that retries
 * subscribes again. Lets tests drive an upstream without waiting on a Room executor.
 */
fun <T> Flow<T>.failOnRequest(failureRequests: Channel<Unit>, onFailure: () -> Unit = {}): Flow<T> = flow {
    coroutineScope {
        launch {
            failureRequests.receive()
            onFailure()
            throw IllegalStateException("Upstream failure")
        }
        emitAll(this@failOnRequest)
    }
}
