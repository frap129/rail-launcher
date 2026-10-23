package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "header_settings")
data class HeaderSettings(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "clock_visible") val clockVisible: Boolean = true,
    @ColumnInfo(name = "date_visible") val dateVisible: Boolean = true,
    @ColumnInfo(name = "clock_style") val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    @ColumnInfo(name = "time_format") val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    @ColumnInfo(name = "show_am_pm") val showAmPm: Boolean = false,
    @ColumnInfo(name = "date_format") val dateFormat: DateFormatPreset = DateFormatPreset.FULL,
    val alignment: HeaderAlignment = HeaderAlignment.START,
    val size: HeaderSize = HeaderSize.DEFAULT,
    @ColumnInfo(name = "time_tap_target") val timeTapTarget: HeaderTapSetting? = null,
    @ColumnInfo(name = "date_tap_target") val dateTapTarget: HeaderTapSetting? = null,
    @ColumnInfo(name = "battery_visibility") val batteryVisibility: BatteryVisibility = BatteryVisibility.NEVER
) {
    val canHideClock: Boolean get() = !clockVisible || dateVisible || batteryVisibility == BatteryVisibility.ALWAYS

    val canHideDate: Boolean get() = !dateVisible || clockVisible || batteryVisibility == BatteryVisibility.ALWAYS

    val resolvedTimeTapTarget: HeaderTapSetting get() = timeTapTarget ?: HeaderTapSetting.SystemDefault

    val resolvedDateTapTarget: HeaderTapSetting get() = dateTapTarget ?: HeaderTapSetting.SystemDefault
}

/** What a Header line opens when tapped. A null database value stands for [SystemDefault]. */
sealed interface HeaderTapSetting {
    data object SystemDefault : HeaderTapSetting

    data object Nothing : HeaderTapSetting

    data class Launchable(val key: String) : HeaderTapSetting
}

enum class ClockStyle {
    CLASSIC,
    BOLD,
    STACKED,
    COMPACT
}

enum class TimeFormat {
    SYSTEM,
    H12,
    H24
}

enum class BatteryVisibility {
    NEVER,
    ALWAYS,
    CHARGING_OR_LOW
}

fun HeaderSettings.withClockVisible(visible: Boolean): HeaderSettings =
    if (!visible && !canHideClock) this else copy(clockVisible = visible)

fun HeaderSettings.withDateVisible(visible: Boolean): HeaderSettings = if (!visible && !canHideDate) this else copy(dateVisible = visible)

/**
 * Reports whether [visibility] can be selected without hiding the Header's last standing part.
 * A conditional battery only stands in when the Clock or Date is visible.
 */
fun canSetBatteryVisibility(visibility: BatteryVisibility, clockVisible: Boolean, dateVisible: Boolean): Boolean =
    visibility == BatteryVisibility.ALWAYS || clockVisible || dateVisible

/**
 * Sets the battery visibility, refusing a non-[BatteryVisibility.ALWAYS] value while the Clock and Date are hidden.
 * Never re-enables a hidden clock.
 */
fun HeaderSettings.withBatteryVisibility(visibility: BatteryVisibility): HeaderSettings =
    if (canSetBatteryVisibility(visibility, clockVisible = clockVisible, dateVisible = dateVisible)) {
        copy(batteryVisibility = visibility)
    } else {
        this
    }

/** Reports whether [visibility] can be selected without hiding this Header's last standing part. */
fun HeaderSettings.canSetBatteryVisibility(visibility: BatteryVisibility): Boolean =
    canSetBatteryVisibility(visibility = visibility, clockVisible = clockVisible, dateVisible = dateVisible)

fun TimeFormat.is24Hour(isSystem24Hour: Boolean): Boolean = when (this) {
    TimeFormat.SYSTEM -> isSystem24Hour
    TimeFormat.H12 -> false
    TimeFormat.H24 -> true
}

enum class DateFormatPreset(val skeleton: String) {
    FULL("EEEEMMMMd"),
    SHORT("EEEMMMd"),
    DAY_AND_MONTH("MMMMd"),
    WEEKDAY_AND_DAY("EEEEd"),
    WEEKDAY("EEEE"),
    NUMERIC("yMd")
}

enum class HeaderAlignment {
    START,
    CENTER,
    END
}

enum class HeaderSize(val timeMultiplier: Float, val secondaryMultiplier: Float) {
    SMALL(0.75f, 0.875f),
    DEFAULT(1f, 1f),
    LARGE(1.25f, 1.125f),
    EXTRA_LARGE(1.5f, 1.25f)
}
