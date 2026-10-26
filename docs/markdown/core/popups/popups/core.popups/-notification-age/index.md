//[popups](../../../index.md)/[core.popups](../index.md)/[NotificationAge](index.md)

# NotificationAge

sealed interface [NotificationAge](index.md)

How long ago a notification was posted, at the granularity a row shows.

#### Inheritors

| |
|---|
| [Now](-now/index.md) |
| [Minutes](-minutes/index.md) |
| [Hours](-hours/index.md) |
| [Days](-days/index.md) |
| [Weeks](-weeks/index.md) |

## Types

| Name | Summary |
|---|---|
| [Days](-days/index.md) | [main]<br>data class [Days](-days/index.md)(val value: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [NotificationAge](index.md) |
| [Hours](-hours/index.md) | [main]<br>data class [Hours](-hours/index.md)(val value: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [NotificationAge](index.md) |
| [Minutes](-minutes/index.md) | [main]<br>data class [Minutes](-minutes/index.md)(val value: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [NotificationAge](index.md) |
| [Now](-now/index.md) | [main]<br>data object [Now](-now/index.md) : [NotificationAge](index.md) |
| [Weeks](-weeks/index.md) | [main]<br>data class [Weeks](-weeks/index.md)(val value: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [NotificationAge](index.md) |
