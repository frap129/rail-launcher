//[data](../../../index.md)/[core.data.widgets](../index.md)/[IHeaderSettingsActions](index.md)/[onCalendarPermissionResult](on-calendar-permission-result.md)

# onCalendarPermissionResult

[main]\
abstract suspend fun [onCalendarPermissionResult](on-calendar-permission-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), canAskAgain: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)

Persists Upcoming events on a grant and reports a denial for the UI to show.
