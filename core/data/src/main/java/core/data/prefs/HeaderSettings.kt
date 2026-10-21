package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "header_settings")
data class HeaderSettings(
    @PrimaryKey val id: Int = 0,
    val clockVisible: Boolean = true,
    val dateVisible: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showAmPm: Boolean = false,
    val dateFormat: DateFormatPreset = DateFormatPreset.FULL,
    val alignment: HeaderAlignment = HeaderAlignment.START,
    val size: HeaderSize = HeaderSize.DEFAULT,
    @ColumnInfo(name = "time_tap_target") val timeTapTarget: HeaderTapSetting? = null,
    @ColumnInfo(name = "date_tap_target") val dateTapTarget: HeaderTapSetting? = null
) {
    val canHideClock: Boolean get() = !clockVisible || dateVisible

    val canHideDate: Boolean get() = !dateVisible || clockVisible

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
