//[popups](../../../index.md)/[core.popups](../index.md)/[PopupPickerState](index.md)

# PopupPickerState

sealed interface [PopupPickerState](index.md)

Whether the Add apps picker is showing and, when it is, its candidates and checked items.

#### Inheritors

| |
|---|
| [Closed](-closed/index.md) |
| [Picking](-picking/index.md) |

## Types

| Name | Summary |
|---|---|
| [Closed](-closed/index.md) | [main]<br>data object [Closed](-closed/index.md) : [PopupPickerState](index.md) |
| [Picking](-picking/index.md) | [main]<br>data class [Picking](-picking/index.md)(val groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, val selectedKeys: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;) : [PopupPickerState](index.md) |
