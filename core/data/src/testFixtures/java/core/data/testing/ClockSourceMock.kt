package core.data.testing

import core.data.time.ClockReading
import core.data.time.IClockSource
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [ClockReading] at [instant] in UTC with an English locale and 24-hour time. */
fun createClockReading(instant: Instant): ClockReading = ClockReading(
    instant = instant,
    zoneId = ZoneId.of("UTC"),
    locale = Locale.US,
    isSystem24Hour = true
)

/** A MockK [IClockSource] reading [readings], which tests emit to, and [now]. */
fun createClockSourceMock(readings: Flow<ClockReading>, now: () -> Instant = { Instant.EPOCH }): IClockSource {
    val mockClockSource: IClockSource = mockk()
    every { mockClockSource.readings } returns readings
    every { mockClockSource.now() } answers { now() }
    return mockClockSource
}

/** A MockK [IClockSource] reading a single [reading], which tests can hold still. */
fun createClockSourceMock(reading: ClockReading): IClockSource =
    createClockSourceMock(readings = MutableStateFlow(reading), now = { reading.instant })

/** A MockK [IClockSource] and the [readings] flow a test emits new readings to. */
data class MutableClockSource(val source: IClockSource, val readings: MutableStateFlow<ClockReading>) {
    /** Emits a reading at [instant], which [IClockSource.now] then reports too unless the source was given its own `now`. */
    fun moveTo(instant: Instant) {
        readings.value = readings.value.copy(instant = instant)
    }
}

/**
 * A [MutableClockSource] whose readings start at [now] (default [TEST_NOW_EPOCH_MS]) in UTC. A test
 * moves the clock with [MutableClockSource.moveTo], or by changing what a custom [now] returns.
 */
fun createMutableClockSourceMock(now: (() -> Instant)? = null): MutableClockSource {
    val readings: MutableStateFlow<ClockReading> =
        MutableStateFlow(createClockReading(instant = now?.invoke() ?: Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)))
    return MutableClockSource(
        source = createClockSourceMock(readings = readings, now = now ?: { readings.value.instant }),
        readings = readings
    )
}

/** A MockK [IClockSource] held at [TEST_NOW_EPOCH_MS]. */
fun createTestClockSourceMock(): IClockSource =
    createClockSourceMock(reading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)))
