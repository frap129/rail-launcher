package core.ui.widgets

import androidx.annotation.StringRes
import core.data.prefs.ClockStyle
import core.data.prefs.isWeekday
import core.ui.R

/** The target the Header's time line opens under [this] Clock style when its Tap action is the system default. */
fun ClockStyle.timeDefaultTapTarget(): HeaderTapTarget = HeaderTapTarget.SystemAlarms

/** The target the Header's date line opens under [this] Clock style when its Tap action is the system default. */
fun ClockStyle.dateDefaultTapTarget(atEpochMillis: Long): HeaderTapTarget = if (isWeekday) {
    HeaderTapTarget.SystemAlarms
} else {
    HeaderTapTarget.SystemCalendar(atEpochMillis = atEpochMillis)
}

/** The string resource describing what [this] target opens, or null when it opens nothing. */
@StringRes
internal fun HeaderTapTarget.clickLabel(): Int? = when (this) {
    HeaderTapTarget.SystemAlarms -> R.string.open_alarms
    is HeaderTapTarget.SystemCalendar -> R.string.open_calendar
    HeaderTapTarget.OpenAgenda -> R.string.open_agenda
    is HeaderTapTarget.OpenEvent -> R.string.open_event
    HeaderTapTarget.SystemBatteryUsage -> R.string.open_battery_usage
    is HeaderTapTarget.LaunchLaunchable -> R.string.open_launchable
    HeaderTapTarget.None -> null
}

/** The argument [this] target's click label takes, or null when it takes none. */
internal fun HeaderTapTarget.clickLabelArg(): String? = (this as? HeaderTapTarget.LaunchLaunchable)?.launchable?.name
