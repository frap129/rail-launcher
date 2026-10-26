//[data](../../../index.md)/[core.data.calendar](../index.md)/[ICalendarEventSourceImpl](index.md)

# ICalendarEventSourceImpl

[main]\
class [ICalendarEventSourceImpl](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, now: () -&gt; [Instant](https://developer.android.com/reference/kotlin/java/time/Instant.html) = Instant::now, zoneId: () -&gt; [ZoneId](https://developer.android.com/reference/kotlin/java/time/ZoneId.html) = ZoneId::systemDefault, queryDispatcher: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Dispatchers.IO) : [ICalendarEventSource](../-i-calendar-event-source/index.md)

## Constructors

| | |
|---|---|
| [ICalendarEventSourceImpl](-i-calendar-event-source-impl.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, now: () -&gt; [Instant](https://developer.android.com/reference/kotlin/java/time/Instant.html) = Instant::now, zoneId: () -&gt; [ZoneId](https://developer.android.com/reference/kotlin/java/time/ZoneId.html) = ZoneId::systemDefault, queryDispatcher: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Dispatchers.IO) |

## Properties

| Name | Summary |
|---|---|
| [calendars](calendars.md) | [main]<br>open override val [calendars](calendars.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[CalendarInfo](../-calendar-info/index.md)&gt;&gt; |
| [hasPermission](has-permission.md) | [main]<br>open override val [hasPermission](has-permission.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt; |

## Functions

| Name | Summary |
|---|---|
| [events](events.md) | [main]<br>open override fun [events](events.md)(lookaheadHours: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[UpcomingEvent](../-upcoming-event/index.md)&gt;&gt;<br>[lookaheadHours](events.md) must already be clamped; the window is recomputed from the current time and zone on every recheck. |
| [refreshPermission](refresh-permission.md) | [main]<br>open override fun [refreshPermission](refresh-permission.md)() |
