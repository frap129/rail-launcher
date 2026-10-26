//[data](../../../index.md)/[core.data.shortcuts](../index.md)/[IShortcutSourceImpl](index.md)/[resolve](resolve.md)

# resolve

[main]\
open override fun [resolve](resolve.md)(keys: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[ShortcutKey](../-shortcut-key/index.md)&gt;): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-map/index.html)&lt;[ShortcutKey](../-shortcut-key/index.md), [ShortcutLaunchable](../-shortcut-launchable/index.md)?&gt;&gt;

The Shortcut for every requested key, or null when it no longer exists. A disabled Shortcut is returned with `isEnabled = false`. Keys whose shortcuts cannot be read are absent from the map.
