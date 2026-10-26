//[data](../../../index.md)/[core.data.folders](../index.md)/[Folder](index.md)

# Folder

[main]\
data class [Folder](index.md)(val folderId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val showInAppList: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = DEFAULT_NAME, val icon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.FolderIcon(shade = folderShade(folderId))) : [Launchable](../../core.data.launchables.model/-launchable/index.md)

A Launchable that only opens a Pop-up listing the Launchables the user put in it.

## Constructors

| | |
|---|---|
| [Folder](-folder.md) | [main]<br>constructor(folderId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), showInAppList: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = DEFAULT_NAME, icon: [Icon](../../core.data.icons.model/-icon/index.md) = Icon.FolderIcon(shade = folderShade(folderId))) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [defaultIcon](default-icon.md) | [main]<br>open override val [defaultIcon](default-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [defaultName](default-name.md) | [main]<br>open override val [defaultName](default-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [folderId](folder-id.md) | [main]<br>val [folderId](folder-id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [icon](icon.md) | [main]<br>open override val [icon](icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [isInAppList](is-in-app-list.md) | [main]<br>val [isInAppList](is-in-app-list.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Whether the user chose to show this Folder in the App List. |
| [key](key.md) | [main]<br>open override val [key](key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [name](name.md) | [main]<br>open override val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [shade](shade.md) | [main]<br>val [shade](shade.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)<br>The derived accent shade; never stored. |
| [showInAppList](show-in-app-list.md) | [main]<br>val [showInAppList](show-in-app-list.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [main]<br>open override fun [launch](launch.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [withCustomization](with-customization.md) | [main]<br>open override fun [withCustomization](with-customization.md)(appearance: [LaunchableAppearance](../../core.data.launchables.model/-launchable-appearance/index.md)): [Folder](index.md)<br>Returns a copy of this Launchable showing [appearance](with-customization.md). |
