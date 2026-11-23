package core.ui.widgets

import core.data.prefs.ClockStyle

/** The target the Header's date line opens under [this] Clock style when its Tap action is the system default. */
fun ClockStyle.dateDefaultTapTarget(atEpochMillis: Long): HeaderTapTarget = if (this == ClockStyle.WEEKDAY) {
    HeaderTapTarget.SystemAlarms
} else {
    HeaderTapTarget.SystemCalendar(atEpochMillis = atEpochMillis)
}
