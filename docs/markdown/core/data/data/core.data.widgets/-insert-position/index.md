//[data](../../../index.md)/[core.data.widgets](../index.md)/[InsertPosition](index.md)

# InsertPosition

sealed interface [InsertPosition](index.md)

Where a newly added App Widget place goes in the Widget Area.

#### Inheritors

| |
|---|
| [Below](-below/index.md) |
| [End](-end/index.md) |
| [IntoStack](-into-stack/index.md) |

## Types

| Name | Summary |
|---|---|
| [Below](-below/index.md) | [main]<br>data class [Below](-below/index.md)(val placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [InsertPosition](index.md)<br>Directly below the place with [placeId](-below/place-id.md), shifting later places down. |
| [End](-end/index.md) | [main]<br>data object [End](-end/index.md) : [InsertPosition](index.md)<br>At the end of the Widget Area. |
| [IntoStack](-into-stack/index.md) | [main]<br>data class [IntoStack](-into-stack/index.md)(val placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [InsertPosition](index.md)<br>Appended as a new page of the Widget Stack in [placeId](-into-stack/place-id.md). |
