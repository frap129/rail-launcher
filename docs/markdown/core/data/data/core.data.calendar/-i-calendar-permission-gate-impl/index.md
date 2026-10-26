//[data](../../../index.md)/[core.data.calendar](../index.md)/[ICalendarPermissionGateImpl](index.md)

# ICalendarPermissionGateImpl

[main]\
class [ICalendarPermissionGateImpl](index.md)(calendarEventSource: [ICalendarEventSource](../-i-calendar-event-source/index.md)) : [ICalendarPermissionGate](../-i-calendar-permission-gate/index.md)

Memoizes a permanent denial until the system reports that permission is held again.

## Constructors

| | |
|---|---|
| [ICalendarPermissionGateImpl](-i-calendar-permission-gate-impl.md) | [main]<br>constructor(calendarEventSource: [ICalendarEventSource](../-i-calendar-event-source/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [decideEnable](decide-enable.md) | [main]<br>open override fun [decideEnable](decide-enable.md)(): [CalendarPermissionDecision](../-calendar-permission-decision/index.md) |
| [onPermissionResult](on-permission-result.md) | [main]<br>open override fun [onPermissionResult](on-permission-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), canAskAgain: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [CalendarPermissionDecision](../-calendar-permission-decision/index.md) |
| [refreshPermission](refresh-permission.md) | [main]<br>open override fun [refreshPermission](refresh-permission.md)() |
