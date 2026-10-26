//[data](../../../index.md)/[core.data.widgets](../index.md)/[PlacedWidget](index.md)

# PlacedWidget

sealed interface [PlacedWidget](index.md)

A Widget stored in a [WidgetPlace](../-widget-place/index.md).

#### Inheritors

| |
|---|
| [Header](-header/index.md) |
| [AppWidget](-app-widget/index.md) |

## Types

| Name | Summary |
|---|---|
| [AppWidget](-app-widget/index.md) | [main]<br>data class [AppWidget](-app-widget/index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val appWidgetId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val provider: [WidgetProvider](../-widget-provider/index.md), val profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val extendsUnderRail: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val availability: [WidgetAvailability](../-widget-availability/index.md) = WidgetAvailability.Available, val defaultHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null) : [PlacedWidget](index.md)<br>A Widget hosted from another app. |
| [Header](-header/index.md) | [main]<br>data class [Header](-header/index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [PlacedWidget](index.md)<br>The built-in clock and date Widget. |

## Properties

| Name | Summary |
|---|---|
| [id](id.md) | [main]<br>abstract val [id](id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |

## Functions

| Name | Summary |
|---|---|
| [resizeBounds](../resize-bounds.md) | [main]<br>fun [PlacedWidget](index.md).[resizeBounds](../resize-bounds.md)(headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, providerInfo: ([PlacedWidget.AppWidget](-app-widget/index.md)) -&gt; [WidgetProviderInfo](../-widget-provider-info/index.md)?): [ResizeBounds](../-resize-bounds/index.md)<br>The bounds of one Widget Stack page; a Header contributes its measured height as a minimum. |
