package core.data.testing

import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

class FakeBatterySource(initialStatus: BatteryStatus? = null) : IBatterySource {
    private val statuses: MutableSharedFlow<BatteryStatus> = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
    private val subscribers: MutableStateFlow<Int> = MutableStateFlow(0)

    val activeSubscribers: Int get() = subscribers.value

    override val status: Flow<BatteryStatus> = statuses
        .onStart { subscribers.update { count -> count + 1 } }
        .onCompletion { subscribers.update { count -> count - 1 } }

    init {
        initialStatus?.let { status -> statuses.tryEmit(status) }
    }

    suspend fun emit(status: BatteryStatus) {
        statuses.emit(status)
    }
}
