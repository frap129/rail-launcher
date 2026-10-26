//[data](../../../index.md)/[core.data.widgets](../index.md)/[IHeaderSettingsActions](index.md)

# IHeaderSettingsActions

interface [IHeaderSettingsActions](index.md)

Applies the Header setting changes shared by the Home menus and the Widgets settings screen.

#### Inheritors

| |
|---|
| [IHeaderSettingsActionsImpl](../-i-header-settings-actions-impl/index.md) |

## Functions

| Name | Summary |
|---|---|
| [onCalendarPermissionResult](on-calendar-permission-result.md) | [main]<br>abstract suspend fun [onCalendarPermissionResult](on-calendar-permission-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), canAskAgain: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)<br>Persists Upcoming events on a grant and reports a denial for the UI to show. |
| [refreshCalendarPermission](refresh-calendar-permission.md) | [main]<br>abstract fun [refreshCalendarPermission](refresh-calendar-permission.md)() |
| [setBatteryVisibility](set-battery-visibility.md) | [main]<br>abstract suspend fun [setBatteryVisibility](set-battery-visibility.md)(visibility: [BatteryVisibility](../../core.data.prefs/-battery-visibility/index.md)) |
| [setClockStyle](set-clock-style.md) | [main]<br>abstract suspend fun [setClockStyle](set-clock-style.md)(style: [ClockStyle](../../core.data.prefs/-clock-style/index.md)) |
| [setClockVisible](set-clock-visible.md) | [main]<br>abstract suspend fun [setClockVisible](set-clock-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setDateVisible](set-date-visible.md) | [main]<br>abstract suspend fun [setDateVisible](set-date-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setUpcomingEventsEnabled](set-upcoming-events-enabled.md) | [main]<br>abstract suspend fun [setUpcomingEventsEnabled](set-upcoming-events-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)<br>Persists Upcoming events as disabled when [enabled](set-upcoming-events-enabled.md) is false, otherwise asks the permission gate first. |
