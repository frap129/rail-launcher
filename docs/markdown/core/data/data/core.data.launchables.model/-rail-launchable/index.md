//[data](../../../index.md)/[core.data.launchables.model](../index.md)/[RailLaunchable](index.md)

# RailLaunchable

[main]\
data class [RailLaunchable](index.md)(val defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md), deeplink: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, val icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) : [Launchable](../-launchable/index.md)

## Constructors

| | |
|---|---|
| [RailLaunchable](-rail-launchable.md) | [main]<br>constructor(defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), defaultIcon: [Icon](../../core.data.icons.model/-icon/index.md), deeplink: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, icon: [Icon](../../core.data.icons.model/-icon/index.md) = defaultIcon) |

## Properties

| Name | Summary |
|---|---|
| [defaultIcon](default-icon.md) | [main]<br>open override val [defaultIcon](default-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [defaultName](default-name.md) | [main]<br>open override val [defaultName](default-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [icon](icon.md) | [main]<br>open override val [icon](icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [key](key.md) | [main]<br>open override val [key](key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [name](name.md) | [main]<br>open override val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [main]<br>open override fun [launch](launch.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |
| [withCustomization](with-customization.md) | [main]<br>open override fun [withCustomization](with-customization.md)(appearance: [LaunchableAppearance](../-launchable-appearance/index.md)): [RailLaunchable](index.md)<br>Returns a copy of this Launchable showing [appearance](with-customization.md). |
