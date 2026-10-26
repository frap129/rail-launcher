//[data](../../../index.md)/[core.data.calendar](../index.md)/[ICalendarEventSource](index.md)

# ICalendarEventSource

interface [ICalendarEventSource](index.md)

#### Inheritors

| |
|---|
| [ICalendarEventSourceImpl](../-i-calendar-event-source-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [calendars](calendars.md) | [main]<br>abstract val [calendars](calendars.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[CalendarInfo](../-calendar-info/index.md)&gt;&gt; |
| [hasPermission](has-permission.md) | [main]<br>abstract val [hasPermission](has-permission.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt; |

## Functions

| Name | Summary |
|---|---|
| [events](events.md) | [main]<br>abstract fun [events](events.md)(lookaheadHours: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[UpcomingEvent](../-upcoming-event/index.md)&gt;&gt;<br>[lookaheadHours](events.md) must already be clamped; the window is recomputed from the current time and zone on every recheck. |
| [refreshPermission](refresh-permission.md) | [main]<br>abstract fun [refreshPermission](refresh-permission.md)() |
