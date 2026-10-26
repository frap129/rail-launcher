//[data](../../index.md)/[core.data.prefs](index.md)/[canSetBatteryVisibility](can-set-battery-visibility.md)

# canSetBatteryVisibility

[main]\
fun [canSetBatteryVisibility](can-set-battery-visibility.md)(visibility: [BatteryVisibility](-battery-visibility/index.md), clockVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), dateVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)

Reports whether [visibility](can-set-battery-visibility.md) can be selected without hiding the Header's last standing part. A conditional battery only stands in when the Clock or Date is visible.

[main]\
fun [HeaderSettings](-header-settings/index.md).[canSetBatteryVisibility](can-set-battery-visibility.md)(visibility: [BatteryVisibility](-battery-visibility/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)

Reports whether [visibility](can-set-battery-visibility.md) can be selected without hiding this Header's last standing part.
