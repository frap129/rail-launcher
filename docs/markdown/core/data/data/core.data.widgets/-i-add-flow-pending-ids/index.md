//[data](../../../index.md)/[core.data.widgets](../index.md)/[IAddFlowPendingIds](index.md)

# IAddFlowPendingIds

interface [IAddFlowPendingIds](index.md)

The system ids allocated by an App Widget add flow that has not completed yet.

#### Inheritors

| |
|---|
| [IAddFlowPendingIdsImpl](../-i-add-flow-pending-ids-impl/index.md) |

## Functions

| Name | Summary |
|---|---|
| [get](get.md) | [main]<br>abstract suspend fun [get](get.md)(): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;<br>Every id reserved by an add flow that has not finished. |
| [set](set.md) | [main]<br>abstract suspend fun [set](set.md)(ids: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;)<br>Replaces the reserved ids. |
