//[widgets](../../../index.md)/[core.widgets](../index.md)/[HeaderTapTarget](index.md)

# HeaderTapTarget

sealed interface [HeaderTapTarget](index.md)

#### Inheritors

| |
|---|
| [SystemAlarms](-system-alarms/index.md) |
| [SystemBatteryUsage](-system-battery-usage/index.md) |
| [SystemCalendar](-system-calendar/index.md) |
| [OpenEvent](-open-event/index.md) |
| [LaunchLaunchable](-launch-launchable/index.md) |
| [None](-none/index.md) |

## Types

| Name | Summary |
|---|---|
| [LaunchLaunchable](-launch-launchable/index.md) | [main]<br>data class [LaunchLaunchable](-launch-launchable/index.md)(val launchable: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [HeaderTapTarget](index.md) |
| [None](-none/index.md) | [main]<br>data object [None](-none/index.md) : [HeaderTapTarget](index.md) |
| [OpenEvent](-open-event/index.md) | [main]<br>data class [OpenEvent](-open-event/index.md)(val eventId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val beginMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val endMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [HeaderTapTarget](index.md) |
| [SystemAlarms](-system-alarms/index.md) | [main]<br>data object [SystemAlarms](-system-alarms/index.md) : [HeaderTapTarget](index.md) |
| [SystemBatteryUsage](-system-battery-usage/index.md) | [main]<br>data object [SystemBatteryUsage](-system-battery-usage/index.md) : [HeaderTapTarget](index.md) |
| [SystemCalendar](-system-calendar/index.md) | [main]<br>data class [SystemCalendar](-system-calendar/index.md)(val atEpochMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [HeaderTapTarget](index.md) |
