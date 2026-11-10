package core.data.time

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface IClockSource {
    /** Emits on collection, on every wall-clock minute, and when time, zone, locale or 12/24-hour format changes. */
    val readings: Flow<ClockReading>

    /** The instant this source reads now. */
    fun now(): Instant
}
