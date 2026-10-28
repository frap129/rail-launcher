package core.data.testing

import core.data.time.ClockReading
import core.data.time.IClockSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow

/** A MockK [IClockSource] reading [readings], which tests emit to. */
fun createClockSourceMock(readings: Flow<ClockReading>): IClockSource {
    val mockClockSource: IClockSource = mockk()
    every { mockClockSource.readings } returns readings
    return mockClockSource
}
