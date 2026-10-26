//[data](../../../index.md)/[core.data.room.dao](../index.md)/[PendingAppWidgetDao](index.md)

# PendingAppWidgetDao

[main]\
abstract class [PendingAppWidgetDao](index.md)

## Constructors

| | |
|---|---|
| [PendingAppWidgetDao](-pending-app-widget-dao.md) | [main]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [deleteAll](delete-all.md) | [main]<br>abstract suspend fun [deleteAll](delete-all.md)() |
| [getIds](get-ids.md) | [main]<br>abstract suspend fun [getIds](get-ids.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)&gt; |
| [insertAll](insert-all.md) | [main]<br>abstract suspend fun [insertAll](insert-all.md)(records: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PendingAppWidgetRecord](../../core.data.widgets/-pending-app-widget-record/index.md)&gt;) |
| [replace](replace.md) | [main]<br>open suspend fun [replace](replace.md)(ids: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)&gt;) |
