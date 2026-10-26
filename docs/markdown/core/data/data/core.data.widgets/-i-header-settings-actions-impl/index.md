//[data](../../../index.md)/[core.data.widgets](../index.md)/[IHeaderSettingsActionsImpl](index.md)

# IHeaderSettingsActionsImpl

[main]\
class [IHeaderSettingsActionsImpl](index.md)(settingsRepository: [IWidgetSettingsRepository](../-i-widget-settings-repository/index.md), calendarPermissionGate: [ICalendarPermissionGate](../../core.data.calendar/-i-calendar-permission-gate/index.md)) : [IHeaderSettingsActions](../-i-header-settings-actions/index.md)

## Constructors

| | |
|---|---|
| [IHeaderSettingsActionsImpl](-i-header-settings-actions-impl.md) | [main]<br>constructor(settingsRepository: [IWidgetSettingsRepository](../-i-widget-settings-repository/index.md), calendarPermissionGate: [ICalendarPermissionGate](../../core.data.calendar/-i-calendar-permission-gate/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [onCalendarPermissionResult](on-calendar-permission-result.md) | [main]<br>open suspend override fun [onCalendarPermissionResult](on-calendar-permission-result.md)(granted: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), canAskAgain: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)<br>Persists Upcoming events on a grant and reports a denial for the UI to show. |
| [refreshCalendarPermission](refresh-calendar-permission.md) | [main]<br>open override fun [refreshCalendarPermission](refresh-calendar-permission.md)() |
| [setBatteryVisibility](set-battery-visibility.md) | [main]<br>open suspend override fun [setBatteryVisibility](set-battery-visibility.md)(visibility: [BatteryVisibility](../../core.data.prefs/-battery-visibility/index.md)) |
| [setClockStyle](set-clock-style.md) | [main]<br>open suspend override fun [setClockStyle](set-clock-style.md)(style: [ClockStyle](../../core.data.prefs/-clock-style/index.md)) |
| [setClockVisible](set-clock-visible.md) | [main]<br>open suspend override fun [setClockVisible](set-clock-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setDateVisible](set-date-visible.md) | [main]<br>open suspend override fun [setDateVisible](set-date-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setUpcomingEventsEnabled](set-upcoming-events-enabled.md) | [main]<br>open suspend override fun [setUpcomingEventsEnabled](set-upcoming-events-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [UpcomingEventsOutcome](../../core.data.calendar/-upcoming-events-outcome/index.md)<br>Persists Upcoming events as disabled when [enabled](set-upcoming-events-enabled.md) is false, otherwise asks the permission gate first. |
