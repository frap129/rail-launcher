//[data](../../../index.md)/[core.data.calendar](../index.md)/[UpcomingEventsOutcome](index.md)

# UpcomingEventsOutcome

sealed interface [UpcomingEventsOutcome](index.md)

The UI-facing result of an Upcoming events change or permission result.

#### Inheritors

| |
|---|
| [NoAction](-no-action/index.md) |
| [RequestPermission](-request-permission/index.md) |
| [PermissionDenied](-permission-denied/index.md) |

## Types

| Name | Summary |
|---|---|
| [NoAction](-no-action/index.md) | [main]<br>data object [NoAction](-no-action/index.md) : [UpcomingEventsOutcome](index.md)<br>The change was applied, so the UI has nothing to show. |
| [PermissionDenied](-permission-denied/index.md) | [main]<br>data class [PermissionDenied](-permission-denied/index.md)(val permanentlyDenied: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [UpcomingEventsOutcome](index.md) |
| [RequestPermission](-request-permission/index.md) | [main]<br>data object [RequestPermission](-request-permission/index.md) : [UpcomingEventsOutcome](index.md) |
