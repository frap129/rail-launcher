//[data](../../../index.md)/[core.data.calendar](../index.md)/[ICalendarPermissionGate](index.md)

# ICalendarPermissionGate

interface [ICalendarPermissionGate](index.md)

Decides whether Upcoming events can be enabled, need a permission request, or stay blocked.

#### Inheritors

| |
|---|
| [ICalendarPermissionGateImpl](../-i-calendar-permission-gate-impl/index.md) |

## Functions

| Name | Summary |
|---|---|
| [decideEnable](decide-enable.md) | [main]<br>abstract fun [decideEnable](decide-enable.md)(): [CalendarPermissionDecision](../-calendar-permission-decision/index.md) |
| [onPermissionResult](on-permission-result.md) | [main]<br>abstract fun [onPermissionResult](on-permission-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), canAskAgain: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [CalendarPermissionDecision](../-calendar-permission-decision/index.md) |
| [refreshPermission](refresh-permission.md) | [main]<br>abstract fun [refreshPermission](refresh-permission.md)() |
