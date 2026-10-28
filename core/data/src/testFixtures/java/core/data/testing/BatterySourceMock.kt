package core.data.testing

import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IBatterySource] reading [status], which tests emit to. */
fun createBatterySourceMock(status: Flow<BatteryStatus>): IBatterySource {
    val mockBatterySource: IBatterySource = mockk()
    every { mockBatterySource.status } returns status
    return mockBatterySource
}

/** A MockK [IBatterySource] reporting a single [status]. */
fun createBatterySourceMock(status: BatteryStatus): IBatterySource = createBatterySourceMock(status = MutableStateFlow(status))
