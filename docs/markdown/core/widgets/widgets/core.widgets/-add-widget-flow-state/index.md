//[widgets](../../../index.md)/[core.widgets](../index.md)/[AddWidgetFlowState](index.md)

# AddWidgetFlowState

sealed interface [AddWidgetFlowState](index.md)

#### Inheritors

| |
|---|
| [Idle](-idle/index.md) |
| [ChoosingPlacement](-choosing-placement/index.md) |
| [Picking](-picking/index.md) |
| [Binding](-binding/index.md) |
| [Configuring](-configuring/index.md) |

## Types

| Name | Summary |
|---|---|
| [Binding](-binding/index.md) | [main]<br>data class [Binding](-binding/index.md)(val widget: [PendingWidget](../-pending-widget/index.md)) : [AddWidgetFlowState](index.md) |
| [ChoosingPlacement](-choosing-placement/index.md) | [main]<br>data class [ChoosingPlacement](-choosing-placement/index.md)(val origin: [AddWidgetOrigin](../-add-widget-origin/index.md), val canAddToStack: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [AddWidgetFlowState](index.md)<br>The Add below / Add next to choice shown before the picker. |
| [Configuring](-configuring/index.md) | [main]<br>data class [Configuring](-configuring/index.md)(val widget: [PendingWidget](../-pending-widget/index.md)) : [AddWidgetFlowState](index.md) |
| [Idle](-idle/index.md) | [main]<br>data object [Idle](-idle/index.md) : [AddWidgetFlowState](index.md) |
| [Picking](-picking/index.md) | [main]<br>data class [Picking](-picking/index.md)(val origin: [AddWidgetOrigin](../-add-widget-origin/index.md), val query: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PickerGroup](../-picker-group/index.md)&gt;) : [AddWidgetFlowState](index.md) |
