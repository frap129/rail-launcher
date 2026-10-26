//[data](../../../index.md)/[core.data.widgets](../index.md)/[IAddFlowPendingIdsImpl](index.md)

# IAddFlowPendingIdsImpl

[main]\
class [IAddFlowPendingIdsImpl](index.md)(dao: [PendingAppWidgetDao](../../core.data.room.dao/-pending-app-widget-dao/index.md)) : [IAddFlowPendingIds](../-i-add-flow-pending-ids/index.md)

Stores the pending ids in Room so startup reconciliation does not free an id whose configuration activity is returning after process recreation.

## Constructors

| | |
|---|---|
| [IAddFlowPendingIdsImpl](-i-add-flow-pending-ids-impl.md) | [main]<br>constructor(dao: [PendingAppWidgetDao](../../core.data.room.dao/-pending-app-widget-dao/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [get](get.md) | [main]<br>open suspend override fun [get](get.md)(): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;<br>Every id reserved by an add flow that has not finished. |
| [set](set.md) | [main]<br>open suspend override fun [set](set.md)(ids: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;)<br>Replaces the reserved ids. |
