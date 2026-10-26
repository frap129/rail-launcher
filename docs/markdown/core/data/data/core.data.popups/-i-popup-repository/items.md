//[data](../../../index.md)/[core.data.popups](../index.md)/[IPopupRepository](index.md)/[items](items.md)

# items

[main]\
abstract fun [items](items.md)(ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PopupItem](../-popup-item/index.md)&gt;&gt;

The items added to [ownerKey](items.md)'s Pop-up, in the user's order. An item whose Launchable or Shortcut no longer exists is removed by the repository and never emitted. Collecting is a pure read.
