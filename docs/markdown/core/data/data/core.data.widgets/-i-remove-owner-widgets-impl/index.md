//[data](../../../index.md)/[core.data.widgets](../index.md)/[IRemoveOwnerWidgetsImpl](index.md)

# IRemoveOwnerWidgetsImpl

[main]\
class [IRemoveOwnerWidgetsImpl](index.md)(repository: [IWidgetAreaRepository](../-i-widget-area-repository/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) : [IRemoveOwnerWidgets](../-i-remove-owner-widgets/index.md)

Removes every Widget owned by a Pop-up and frees their system App Widget ids.

## Constructors

| | |
|---|---|
| [IRemoveOwnerWidgetsImpl](-i-remove-owner-widgets-impl.md) | [main]<br>constructor(repository: [IWidgetAreaRepository](../-i-widget-area-repository/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [invoke](invoke.md) | [main]<br>open suspend operator override fun [invoke](invoke.md)(ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt; |
