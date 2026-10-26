//[data](../../../../index.md)/[core.data.widgets](../../index.md)/[PlacedWidget](../index.md)/[AppWidget](index.md)

# AppWidget

[main]\
data class [AppWidget](index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val appWidgetId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val provider: [WidgetProvider](../../-widget-provider/index.md), val profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val extendsUnderRail: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val availability: [WidgetAvailability](../../-widget-availability/index.md) = WidgetAvailability.Available, val defaultHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null) : [PlacedWidget](../index.md)

A Widget hosted from another app.

## Constructors

| | |
|---|---|
| [AppWidget](-app-widget.md) | [main]<br>constructor(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), appWidgetId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), provider: [WidgetProvider](../../-widget-provider/index.md), profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), extendsUnderRail: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), availability: [WidgetAvailability](../../-widget-availability/index.md) = WidgetAvailability.Available, defaultHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [appWidgetId](app-widget-id.md) | [main]<br>val [appWidgetId](app-widget-id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [availability](availability.md) | [main]<br>val [availability](availability.md): [WidgetAvailability](../../-widget-availability/index.md) |
| [defaultHeightDp](default-height-dp.md) | [main]<br>val [defaultHeightDp](default-height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null |
| [extendsUnderRail](extends-under-rail.md) | [main]<br>val [extendsUnderRail](extends-under-rail.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [id](id.md) | [main]<br>open override val [id](id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [profileSerial](profile-serial.md) | [main]<br>val [profileSerial](profile-serial.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [provider](provider.md) | [main]<br>val [provider](provider.md): [WidgetProvider](../../-widget-provider/index.md) |

## Functions

| Name | Summary |
|---|---|
| [resizeBounds](../../resize-bounds.md) | [main]<br>fun [PlacedWidget](../index.md).[resizeBounds](../../resize-bounds.md)(headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, providerInfo: ([PlacedWidget.AppWidget](index.md)) -&gt; [WidgetProviderInfo](../../-widget-provider-info/index.md)?): [ResizeBounds](../../-resize-bounds/index.md)<br>The bounds of one Widget Stack page; a Header contributes its measured height as a minimum. |
