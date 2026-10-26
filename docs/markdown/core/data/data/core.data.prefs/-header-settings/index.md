//[data](../../../index.md)/[core.data.prefs](../index.md)/[HeaderSettings](index.md)

# HeaderSettings

[main]\
data class [HeaderSettings](index.md)(val id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val clockVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val dateVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val clockStyle: [ClockStyle](../-clock-style/index.md) = ClockStyle.CLASSIC, val timeFormat: [TimeFormat](../-time-format/index.md) = TimeFormat.SYSTEM, val showAmPm: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, val dateFormat: [DateFormatPreset](../-date-format-preset/index.md) = DateFormatPreset.FULL, val alignment: [HeaderAlignment](../-header-alignment/index.md) = HeaderAlignment.START, val size: [HeaderSize](../-header-size/index.md) = HeaderSize.DEFAULT, val timeTapTarget: [HeaderTapSetting](../-header-tap-setting/index.md)? = null, val dateTapTarget: [HeaderTapSetting](../-header-tap-setting/index.md)? = null, val batteryVisibility: [BatteryVisibility](../-battery-visibility/index.md) = BatteryVisibility.NEVER)

## Constructors

| | |
|---|---|
| [HeaderSettings](-header-settings.md) | [main]<br>constructor(id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, clockVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, dateVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, clockStyle: [ClockStyle](../-clock-style/index.md) = ClockStyle.CLASSIC, timeFormat: [TimeFormat](../-time-format/index.md) = TimeFormat.SYSTEM, showAmPm: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, dateFormat: [DateFormatPreset](../-date-format-preset/index.md) = DateFormatPreset.FULL, alignment: [HeaderAlignment](../-header-alignment/index.md) = HeaderAlignment.START, size: [HeaderSize](../-header-size/index.md) = HeaderSize.DEFAULT, timeTapTarget: [HeaderTapSetting](../-header-tap-setting/index.md)? = null, dateTapTarget: [HeaderTapSetting](../-header-tap-setting/index.md)? = null, batteryVisibility: [BatteryVisibility](../-battery-visibility/index.md) = BatteryVisibility.NEVER) |

## Properties

| Name | Summary |
|---|---|
| [alignment](alignment.md) | [main]<br>val [alignment](alignment.md): [HeaderAlignment](../-header-alignment/index.md) |
| [batteryVisibility](battery-visibility.md) | [main]<br>val [batteryVisibility](battery-visibility.md): [BatteryVisibility](../-battery-visibility/index.md) |
| [canHideClock](can-hide-clock.md) | [main]<br>val [canHideClock](can-hide-clock.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [canHideDate](can-hide-date.md) | [main]<br>val [canHideDate](can-hide-date.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [clockStyle](clock-style.md) | [main]<br>val [clockStyle](clock-style.md): [ClockStyle](../-clock-style/index.md) |
| [clockVisible](clock-visible.md) | [main]<br>val [clockVisible](clock-visible.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [dateFormat](date-format.md) | [main]<br>val [dateFormat](date-format.md): [DateFormatPreset](../-date-format-preset/index.md) |
| [dateTapTarget](date-tap-target.md) | [main]<br>val [dateTapTarget](date-tap-target.md): [HeaderTapSetting](../-header-tap-setting/index.md)? = null |
| [dateVisible](date-visible.md) | [main]<br>val [dateVisible](date-visible.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [id](id.md) | [main]<br>val [id](id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [resolvedDateTapTarget](resolved-date-tap-target.md) | [main]<br>val [resolvedDateTapTarget](resolved-date-tap-target.md): [HeaderTapSetting](../-header-tap-setting/index.md) |
| [resolvedTimeTapTarget](resolved-time-tap-target.md) | [main]<br>val [resolvedTimeTapTarget](resolved-time-tap-target.md): [HeaderTapSetting](../-header-tap-setting/index.md) |
| [showAmPm](show-am-pm.md) | [main]<br>val [showAmPm](show-am-pm.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [size](size.md) | [main]<br>val [size](size.md): [HeaderSize](../-header-size/index.md) |
| [timeFormat](time-format.md) | [main]<br>val [timeFormat](time-format.md): [TimeFormat](../-time-format/index.md) |
| [timeTapTarget](time-tap-target.md) | [main]<br>val [timeTapTarget](time-tap-target.md): [HeaderTapSetting](../-header-tap-setting/index.md)? = null |

## Functions

| Name | Summary |
|---|---|
| [canSetBatteryVisibility](../can-set-battery-visibility.md) | [main]<br>fun [HeaderSettings](index.md).[canSetBatteryVisibility](../can-set-battery-visibility.md)(visibility: [BatteryVisibility](../-battery-visibility/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Reports whether [visibility](../can-set-battery-visibility.md) can be selected without hiding this Header's last standing part. |
| [withBatteryVisibility](../with-battery-visibility.md) | [main]<br>fun [HeaderSettings](index.md).[withBatteryVisibility](../with-battery-visibility.md)(visibility: [BatteryVisibility](../-battery-visibility/index.md)): [HeaderSettings](index.md)<br>Sets the battery visibility, refusing a non-[BatteryVisibility.ALWAYS](../-battery-visibility/-a-l-w-a-y-s/index.md) value while the Clock and Date are hidden. Never re-enables a hidden clock. |
| [withClockVisible](../with-clock-visible.md) | [main]<br>fun [HeaderSettings](index.md).[withClockVisible](../with-clock-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [HeaderSettings](index.md) |
| [withDateVisible](../with-date-visible.md) | [main]<br>fun [HeaderSettings](index.md).[withDateVisible](../with-date-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [HeaderSettings](index.md) |
