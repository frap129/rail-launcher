//[data](../../../index.md)/[core.data.calendar](../index.md)/[CalendarPermissionDecision](index.md)

# CalendarPermissionDecision

sealed interface [CalendarPermissionDecision](index.md)

#### Inheritors

| |
|---|
| [Enable](-enable/index.md) |
| [Request](-request/index.md) |
| [Denied](-denied/index.md) |

## Types

| Name | Summary |
|---|---|
| [Denied](-denied/index.md) | [main]<br>data class [Denied](-denied/index.md)(val permanentlyDenied: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [CalendarPermissionDecision](index.md) |
| [Enable](-enable/index.md) | [main]<br>data object [Enable](-enable/index.md) : [CalendarPermissionDecision](index.md) |
| [Request](-request/index.md) | [main]<br>data object [Request](-request/index.md) : [CalendarPermissionDecision](index.md) |
