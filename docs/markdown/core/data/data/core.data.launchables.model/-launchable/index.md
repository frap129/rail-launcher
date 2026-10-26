//[data](../../../index.md)/[core.data.launchables.model](../index.md)/[Launchable](index.md)

# Launchable

abstract class [Launchable](index.md)

#### Inheritors

| |
|---|
| [App](../../core.data.apps.model/-app/index.md) |
| [Folder](../../core.data.folders/-folder/index.md) |
| [RailLaunchable](../-rail-launchable/index.md) |
| [ShortcutLaunchable](../../core.data.shortcuts/-shortcut-launchable/index.md) |

## Constructors

| | |
|---|---|
| [Launchable](-launchable.md) | [main]<br>constructor() |

## Properties

| Name | Summary |
|---|---|
| [defaultIcon](default-icon.md) | [main]<br>abstract val [defaultIcon](default-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [defaultName](default-name.md) | [main]<br>abstract val [defaultName](default-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [icon](icon.md) | [main]<br>abstract val [icon](icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [key](key.md) | [main]<br>abstract val [key](key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [name](name.md) | [main]<br>abstract val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [main]<br>abstract fun [launch](launch.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [withCustomization](with-customization.md) | [main]<br>abstract fun [withCustomization](with-customization.md)(appearance: [LaunchableAppearance](../-launchable-appearance/index.md)): [Launchable](index.md)<br>Returns a copy of this Launchable showing [appearance](with-customization.md). |
