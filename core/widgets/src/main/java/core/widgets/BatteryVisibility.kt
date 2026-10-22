package core.widgets

import androidx.annotation.StringRes
import core.data.prefs.BatteryVisibility
import core.ui.R as CoreUiR

@StringRes
fun BatteryVisibility.labelResource(): Int = when (this) {
    BatteryVisibility.NEVER -> CoreUiR.string.battery_never
    BatteryVisibility.ALWAYS -> CoreUiR.string.battery_always
    BatteryVisibility.CHARGING_OR_LOW -> CoreUiR.string.battery_charging_or_low
}
