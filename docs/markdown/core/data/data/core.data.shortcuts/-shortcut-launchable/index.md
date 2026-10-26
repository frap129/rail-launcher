//[data](../../../index.md)/[core.data.shortcuts](../index.md)/[ShortcutLaunchable](index.md)

# ShortcutLaunchable

[main]\
data class [ShortcutLaunchable](index.md)(val packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val shortcutId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val shortLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val longLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val isEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = longLabel.ifBlank { shortLabel }, val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, val defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.ShortcutIcon(packageName = packageName, shortcutId = shortcutId, profile = profile), val icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) : [Launchable](../../core.data.launchables.model/-launchable/index.md)

A launcher shortcut published by an app in a user profile.

## Constructors

| | |
|---|---|
| [ShortcutLaunchable](-shortcut-launchable.md) | [main]<br>constructor(packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), shortcutId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, shortLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), longLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), isEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = longLabel.ifBlank { shortLabel }, name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.ShortcutIcon(packageName = packageName, shortcutId = shortcutId, profile = profile), icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) |

## Properties

| Name | Summary |
|---|---|
| [defaultIcon](default-icon.md) | [main]<br>open override val [defaultIcon](default-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [defaultName](default-name.md) | [main]<br>open override val [defaultName](default-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [icon](icon.md) | [main]<br>open override val [icon](icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [isEnabled](is-enabled.md) | [main]<br>val [isEnabled](is-enabled.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [key](key.md) | [main]<br>open override val [key](key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [longLabel](long-label.md) | [main]<br>val [longLabel](long-label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [name](name.md) | [main]<br>open override val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [packageName](package-name.md) | [main]<br>val [packageName](package-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [profile](profile.md) | [main]<br>val [profile](profile.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |
| [shortcutId](shortcut-id.md) | [main]<br>val [shortcutId](shortcut-id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [shortLabel](short-label.md) | [main]<br>val [shortLabel](short-label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [main]<br>open override fun [launch](launch.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [withCustomization](with-customization.md) | [main]<br>open override fun [withCustomization](with-customization.md)(appearance: [LaunchableAppearance](../../core.data.launchables.model/-launchable-appearance/index.md)): [ShortcutLaunchable](index.md)<br>Returns a copy of this Launchable showing [appearance](with-customization.md). |
