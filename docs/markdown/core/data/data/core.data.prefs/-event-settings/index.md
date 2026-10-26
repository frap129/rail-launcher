//[data](../../../index.md)/[core.data.prefs](../index.md)/[EventSettings](index.md)

# EventSettings

[main]\
data class [EventSettings](index.md)(val id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, val lookaheadHours: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = DEFAULT_LOOKAHEAD_HOURS, val maxEvents: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = MIN_EVENT_COUNT, val includeAllDay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val excludedCalendarIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptySet())

## Constructors

| | |
|---|---|
| [EventSettings](-event-settings.md) | [main]<br>constructor(id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, lookaheadHours: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = DEFAULT_LOOKAHEAD_HOURS, maxEvents: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = MIN_EVENT_COUNT, includeAllDay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, excludedCalendarIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptySet()) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [enabled](enabled.md) | [main]<br>val [enabled](enabled.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [excludedCalendarIds](excluded-calendar-ids.md) | [main]<br>val [excludedCalendarIds](excluded-calendar-ids.md): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; |
| [id](id.md) | [main]<br>val [id](id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [includeAllDay](include-all-day.md) | [main]<br>val [includeAllDay](include-all-day.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [lookaheadHours](lookahead-hours.md) | [main]<br>val [lookaheadHours](lookahead-hours.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [maxEvents](max-events.md) | [main]<br>val [maxEvents](max-events.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [resolvedMaxEvents](resolved-max-events.md) | [main]<br>val [resolvedMaxEvents](resolved-max-events.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [effectiveLookaheadHours](../../core.data.calendar/effective-lookahead-hours.md) | [main]<br>fun [EventSettings](index.md).[effectiveLookaheadHours](../../core.data.calendar/effective-lookahead-hours.md)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)<br>Clamps a lookahead so callers can pass an untrusted stored value without opening a huge query window. |
| [withValidMaxEvents](with-valid-max-events.md) | [main]<br>fun [withValidMaxEvents](with-valid-max-events.md)(): [EventSettings](index.md) |
