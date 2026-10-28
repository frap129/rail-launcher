package core.data.testing

import core.data.time.ClockReading
import core.data.time.IClockSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IClockSource] reading [readings], which tests emit to. */
fun createClockSourceMock(readings: Flow<ClockReading>): IClockSource {
    val mockClockSource: IClockSource = mockk()
    every { mockClockSource.readings } returns readings
    return mockClockSource
}

/** A MockK [IClockSource] reading a single [reading], which tests can hold still. */
fun createClockSourceMock(reading: ClockReading): IClockSource = createClockSourceMock(readings = MutableStateFlow(reading))
