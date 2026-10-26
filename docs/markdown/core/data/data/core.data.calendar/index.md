//[data](../../index.md)/[core.data.calendar](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [CalendarInfo](-calendar-info/index.md) | [main]<br>data class [CalendarInfo](-calendar-info/index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val accountName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val color: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [CalendarPermissionDecision](-calendar-permission-decision/index.md) | [main]<br>sealed interface [CalendarPermissionDecision](-calendar-permission-decision/index.md) |
| [ICalendarEventSource](-i-calendar-event-source/index.md) | [main]<br>interface [ICalendarEventSource](-i-calendar-event-source/index.md) |
| [ICalendarEventSourceImpl](-i-calendar-event-source-impl/index.md) | [main]<br>class [ICalendarEventSourceImpl](-i-calendar-event-source-impl/index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, now: () -&gt; [Instant](https://developer.android.com/reference/kotlin/java/time/Instant.html) = Instant::now, zoneId: () -&gt; [ZoneId](https://developer.android.com/reference/kotlin/java/time/ZoneId.html) = ZoneId::systemDefault, queryDispatcher: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Dispatchers.IO) : [ICalendarEventSource](-i-calendar-event-source/index.md) |
| [ICalendarPermissionGate](-i-calendar-permission-gate/index.md) | [main]<br>interface [ICalendarPermissionGate](-i-calendar-permission-gate/index.md)<br>Decides whether Upcoming events can be enabled, need a permission request, or stay blocked. |
| [ICalendarPermissionGateImpl](-i-calendar-permission-gate-impl/index.md) | [main]<br>class [ICalendarPermissionGateImpl](-i-calendar-permission-gate-impl/index.md)(calendarEventSource: [ICalendarEventSource](-i-calendar-event-source/index.md)) : [ICalendarPermissionGate](-i-calendar-permission-gate/index.md)<br>Memoizes a permanent denial until the system reports that permission is held again. |
| [UpcomingEvent](-upcoming-event/index.md) | [main]<br>data class [UpcomingEvent](-upcoming-event/index.md)(val eventId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val beginMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val endMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val isAllDay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val calendarId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val color: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [UpcomingEventsOutcome](-upcoming-events-outcome/index.md) | [main]<br>sealed interface [UpcomingEventsOutcome](-upcoming-events-outcome/index.md)<br>The UI-facing result of an Upcoming events change or permission result. |

## Properties

| Name | Summary |
|---|---|
| [MAX_EVENT_LOOKAHEAD_HOURS](-m-a-x_-e-v-e-n-t_-l-o-o-k-a-h-e-a-d_-h-o-u-r-s.md) | [main]<br>const val [MAX_EVENT_LOOKAHEAD_HOURS](-m-a-x_-e-v-e-n-t_-l-o-o-k-a-h-e-a-d_-h-o-u-r-s.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 48 |

## Functions

| Name | Summary |
|---|---|
| [calendarKey](calendar-key.md) | [main]<br>fun [calendarKey](calendar-key.md)(calendarId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [effectiveLookaheadHours](effective-lookahead-hours.md) | [main]<br>fun [EventSettings](../core.data.prefs/-event-settings/index.md).[effectiveLookaheadHours](effective-lookahead-hours.md)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)<br>Clamps a lookahead so callers can pass an untrusted stored value without opening a huge query window. |
