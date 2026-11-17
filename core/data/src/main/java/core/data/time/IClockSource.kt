package core.data.time

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface IClockSource {
    /**
     * Emits a reading on collection, on every wall-clock minute, and when time, zone, locale or 12/24-hour format changes.
     * Every collector shares one stream and reads the clock when it joins, so a late collector never receives a stale reading.
     */
    val readings: Flow<ClockReading>

    /** The instant this source reads now. */
    fun now(): Instant
}
