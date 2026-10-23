package core.widgets

import androidx.annotation.StringRes
import core.data.prefs.BatteryVisibility

@StringRes
fun BatteryVisibility.labelResource(): Int = when (this) {
    BatteryVisibility.NEVER -> R.string.battery_never
    BatteryVisibility.ALWAYS -> R.string.battery_always
    BatteryVisibility.CHARGING_OR_LOW -> R.string.battery_charging_or_low
}
