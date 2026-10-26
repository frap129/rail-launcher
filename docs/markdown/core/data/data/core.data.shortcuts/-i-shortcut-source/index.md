//[data](../../../index.md)/[core.data.shortcuts](../index.md)/[IShortcutSource](index.md)

# IShortcutSource

interface [IShortcutSource](index.md)

A live source of the shortcuts published by an app in one user profile.

#### Inheritors

| |
|---|
| [IShortcutSourceImpl](../-i-shortcut-source-impl/index.md) |

## Functions

| Name | Summary |
|---|---|
| [appsWithShortcuts](apps-with-shortcuts.md) | [main]<br>abstract fun [appsWithShortcuts](apps-with-shortcuts.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[AppShortcuts](../-app-shortcuts/index.md)&gt;&gt;<br>Every App in the launchable list with its shortcuts. Only queries while collected. |
| [pin](pin.md) | [main]<br>abstract suspend fun [pin](pin.md)(packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, ids: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;)<br>Pins [ids](pin.md) for the package in [profile](pin.md), or unpins every shortcut when [ids](pin.md) is empty. |
| [resolve](resolve.md) | [main]<br>abstract fun [resolve](resolve.md)(keys: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[ShortcutKey](../-shortcut-key/index.md)&gt;): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-map/index.html)&lt;[ShortcutKey](../-shortcut-key/index.md), [ShortcutLaunchable](../-shortcut-launchable/index.md)?&gt;&gt;<br>The Shortcut for every requested key, or null when it no longer exists. A disabled Shortcut is returned with `isEnabled = false`. Keys whose shortcuts cannot be read are absent from the map. |
| [shortcuts](shortcuts.md) | [main]<br>abstract fun [shortcuts](shortcuts.md)(packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[ShortcutsResult](../-shortcuts-result/index.md)&gt; |
