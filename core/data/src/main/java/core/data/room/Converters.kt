package core.data.room

import android.content.ComponentName
import androidx.room.TypeConverter
import core.data.icons.model.Icon
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetKind

private const val TAP_TARGET_NONE: String = "none"
private const val TAP_TARGET_LAUNCHABLE_PREFIX: String = "launchable:"

class Converters {
    @TypeConverter
    fun fromComponentNameToString(componentName: ComponentName): String = componentName.flattenToString()

    @TypeConverter
    fun fromStringToComponentName(componentName: String): ComponentName = ComponentName.unflattenFromString(componentName)!!

    @TypeConverter
    fun fromApplicationIconToString(appIcon: Icon.ApplicationIcon): String = appIcon.packageName

    @TypeConverter
    fun fromStringToApplicationIcon(packageName: String): Icon.ApplicationIcon = Icon.ApplicationIcon(packageName)

    @TypeConverter
    fun fromWidgetKindToString(kind: WidgetKind): String = kind.name

    @TypeConverter
    fun fromStringToWidgetKind(kind: String): WidgetKind =
        WidgetKind.entries.firstOrNull { entry -> entry.name == kind } ?: WidgetKind.UNKNOWN

    @TypeConverter
    fun fromWidgetAreaHeightLimitToString(limit: WidgetAreaHeightLimit): String = limit.name

    @TypeConverter
    fun fromStringToWidgetAreaHeightLimit(limit: String): WidgetAreaHeightLimit = WidgetAreaHeightLimit.fromName(limit)

    @TypeConverter
    fun fromClockStyleToString(style: ClockStyle): String = style.name

    @TypeConverter
    fun fromStringToClockStyle(style: String): ClockStyle =
        ClockStyle.entries.firstOrNull { entry -> entry.name == style } ?: ClockStyle.CLASSIC

    @TypeConverter
    fun fromTimeFormatToString(format: TimeFormat): String = format.name

    @TypeConverter
    fun fromStringToTimeFormat(format: String): TimeFormat =
        TimeFormat.entries.firstOrNull { entry -> entry.name == format } ?: TimeFormat.SYSTEM

    @TypeConverter
    fun fromDateFormatPresetToString(format: DateFormatPreset): String = format.name

    @TypeConverter
    fun fromStringToDateFormatPreset(format: String): DateFormatPreset =
        DateFormatPreset.entries.firstOrNull { entry -> entry.name == format } ?: DateFormatPreset.FULL

    @TypeConverter
    fun fromHeaderAlignmentToString(alignment: HeaderAlignment): String = alignment.name

    @TypeConverter
    fun fromStringToHeaderAlignment(alignment: String): HeaderAlignment =
        HeaderAlignment.entries.firstOrNull { entry -> entry.name == alignment } ?: HeaderAlignment.START

    @TypeConverter
    fun fromHeaderSizeToString(size: HeaderSize): String = size.name

    @TypeConverter
    fun fromStringToHeaderSize(size: String): HeaderSize =
        HeaderSize.entries.firstOrNull { entry -> entry.name == size } ?: HeaderSize.DEFAULT

    @TypeConverter
    fun fromBatteryVisibilityToString(visibility: BatteryVisibility): String = visibility.name

    @TypeConverter
    fun fromStringToBatteryVisibility(visibility: String): BatteryVisibility =
        BatteryVisibility.entries.firstOrNull { entry -> entry.name == visibility } ?: BatteryVisibility.NEVER

    @TypeConverter
    fun fromHeaderTapSettingToString(setting: HeaderTapSetting?): String? = when (setting) {
        null, HeaderTapSetting.SystemDefault -> null
        HeaderTapSetting.Nothing -> TAP_TARGET_NONE
        is HeaderTapSetting.Launchable -> "$TAP_TARGET_LAUNCHABLE_PREFIX${setting.key}"
    }

    @TypeConverter
    fun fromStringToHeaderTapSetting(value: String?): HeaderTapSetting = when {
        value == null -> HeaderTapSetting.SystemDefault
        value == TAP_TARGET_NONE -> HeaderTapSetting.Nothing
        value.startsWith(TAP_TARGET_LAUNCHABLE_PREFIX) -> HeaderTapSetting.Launchable(value.removePrefix(TAP_TARGET_LAUNCHABLE_PREFIX))
        else -> HeaderTapSetting.SystemDefault
    }

    @TypeConverter
    fun fromCalendarIdSetToString(ids: Set<String>): String = ids.joinToString(separator = CALENDAR_ID_SEPARATOR)

    @TypeConverter
    fun fromStringToCalendarIdSet(value: String): Set<String> = value.split(CALENDAR_ID_SEPARATOR).filter { id -> id.isNotBlank() }.toSet()
}

private const val CALENDAR_ID_SEPARATOR: String = ","
