//[data](../../../index.md)/[core.data.widgets](../index.md)/[IRemoveAppWidgetImpl](index.md)

# IRemoveAppWidgetImpl

[main]\
class [IRemoveAppWidgetImpl](index.md)(repository: [IWidgetAreaRepository](../-i-widget-area-repository/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) : [IRemoveAppWidget](../-i-remove-app-widget/index.md)

Removes a placed Widget and frees its system App Widget id.

## Constructors

| | |
|---|---|
| [IRemoveAppWidgetImpl](-i-remove-app-widget-impl.md) | [main]<br>constructor(repository: [IWidgetAreaRepository](../-i-widget-area-repository/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [invoke](invoke.md) | [main]<br>open suspend operator override fun [invoke](invoke.md)(widgetId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)): [AppWidgetId](../-app-widget-id/index.md)? |
