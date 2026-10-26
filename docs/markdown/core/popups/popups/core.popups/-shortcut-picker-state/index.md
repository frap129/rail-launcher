//[popups](../../../index.md)/[core.popups](../index.md)/[ShortcutPickerState](index.md)

# ShortcutPickerState

sealed interface [ShortcutPickerState](index.md)

Whether the Add shortcuts picker is showing and, when it is, its candidates and checked items.

#### Inheritors

| |
|---|
| [Closed](-closed/index.md) |
| [Picking](-picking/index.md) |

## Types

| Name | Summary |
|---|---|
| [Closed](-closed/index.md) | [main]<br>data object [Closed](-closed/index.md) : [ShortcutPickerState](index.md) |
| [Picking](-picking/index.md) | [main]<br>data class [Picking](-picking/index.md)(val groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[ShortcutPickerGroup](../-shortcut-picker-group/index.md)&gt;, val selectedKeys: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;, val shortcutsUnavailable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [ShortcutPickerState](index.md) |
