//[data](../../../index.md)/[core.data.widgets](../index.md)/[AddAppWidget](index.md)

# AddAppWidget

[main]\
data class [AddAppWidget](index.md)(val appWidgetId: [AppWidgetId](../-app-widget-id/index.md), val provider: [WidgetProvider](../-widget-provider/index.md), val profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val insertAt: [InsertPosition](../-insert-position/index.md), val defaultHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null)

Everything needed to place a bound App Widget.

## Constructors

| | |
|---|---|
| [AddAppWidget](-add-app-widget.md) | [main]<br>constructor(appWidgetId: [AppWidgetId](../-app-widget-id/index.md), provider: [WidgetProvider](../-widget-provider/index.md), profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), insertAt: [InsertPosition](../-insert-position/index.md), defaultHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [appWidgetId](app-widget-id.md) | [main]<br>val [appWidgetId](app-widget-id.md): [AppWidgetId](../-app-widget-id/index.md) |
| [defaultHeightDp](default-height-dp.md) | [main]<br>val [defaultHeightDp](default-height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [insertAt](insert-at.md) | [main]<br>val [insertAt](insert-at.md): [InsertPosition](../-insert-position/index.md) |
| [ownerKey](owner-key.md) | [main]<br>val [ownerKey](owner-key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null<br>The Pop-up owning the widget, or null when it belongs to the Widget Area. |
| [profileSerial](profile-serial.md) | [main]<br>val [profileSerial](profile-serial.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [provider](provider.md) | [main]<br>val [provider](provider.md): [WidgetProvider](../-widget-provider/index.md) |
