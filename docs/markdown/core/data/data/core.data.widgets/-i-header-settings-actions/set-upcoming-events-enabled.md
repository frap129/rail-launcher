//[data](../../../index.md)/[core.data.widgets](../index.md)/[IHeaderSettingsActions](index.md)/[setUpcomingEventsEnabled](set-upcoming-events-enabled.md)

# setUpcomingEventsEnabled

[main]\
abstract suspend fun [setUpcomingEventsEnabled](set-upcoming-events-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)

Persists Upcoming events as disabled when [enabled](set-upcoming-events-enabled.md) is false, otherwise asks the permission gate first.
