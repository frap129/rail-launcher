//[launcher](../../../index.md)/[feature.launcher.widgets](../index.md)/[WidgetMenuItem](index.md)

# WidgetMenuItem

sealed interface [WidgetMenuItem](index.md)

#### Inheritors

| |
|---|
| [Toggle](-toggle/index.md) |
| [Action](-action/index.md) |
| [Choice](-choice/index.md) |

## Types

| Name | Summary |
|---|---|
| [Action](-action/index.md) | [main]<br>data class [Action](-action/index.md)(val id: [WidgetMenuAction](../-widget-menu-action/index.md), val labelArg: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null) : [WidgetMenuItem](index.md) |
| [Choice](-choice/index.md) | [main]<br>data class [Choice](-choice/index.md)(val id: [WidgetMenuAction](../-widget-menu-action/index.md), val valueLabel: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val options: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetMenuChoiceOption](../-widget-menu-choice-option/index.md)&gt;, val selectedKey: [WidgetMenuChoiceKey](../-widget-menu-choice-key/index.md), val expanded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [WidgetMenuItem](index.md) |
| [Toggle](-toggle/index.md) | [main]<br>data class [Toggle](-toggle/index.md)(val id: [WidgetMenuAction](../-widget-menu-action/index.md), val checked: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val supportingText: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null, val rowOpensAction: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false) : [WidgetMenuItem](index.md) |

## Properties

| Name | Summary |
|---|---|
| [id](id.md) | [main]<br>abstract val [id](id.md): [WidgetMenuAction](../-widget-menu-action/index.md) |
