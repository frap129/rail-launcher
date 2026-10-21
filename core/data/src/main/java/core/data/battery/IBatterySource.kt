package core.data.battery

import kotlinx.coroutines.flow.Flow

data class BatteryStatus(val percent: Int, val isCharging: Boolean)

interface IBatterySource {
    val status: Flow<BatteryStatus>
}
