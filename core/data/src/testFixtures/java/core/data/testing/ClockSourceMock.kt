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
