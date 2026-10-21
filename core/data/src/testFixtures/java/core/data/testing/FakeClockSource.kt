package core.data.testing

import core.data.time.ClockReading
import core.data.time.IClockSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeClockSource(initialReading: ClockReading) : IClockSource {
    private val readingFlow: MutableSharedFlow<ClockReading> = MutableSharedFlow(replay = 1)

    init {
        readingFlow.tryEmit(initialReading)
    }

    override val readings: Flow<ClockReading> = readingFlow

    suspend fun emit(reading: ClockReading) {
        readingFlow.emit(reading)
    }
}
