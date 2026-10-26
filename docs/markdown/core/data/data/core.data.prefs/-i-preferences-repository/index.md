//[data](../../../index.md)/[core.data.prefs](../index.md)/[IPreferencesRepository](index.md)

# IPreferencesRepository

interface [IPreferencesRepository](index.md)

#### Inheritors

| |
|---|
| [IPreferencesRepositoryImpl](../-i-preferences-repository-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [launchableCustomizations](launchable-customizations.md) | [main]<br>abstract val [launchableCustomizations](launchable-customizations.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableCustomization](../-launchable-customization/index.md)&gt;&gt; |
| [settings](settings.md) | [main]<br>abstract val [settings](settings.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Settings](../-settings/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [resetItemName](reset-item-name.md) | [main]<br>abstract fun [resetItemName](reset-item-name.md)(item: [Launchable](../../core.data.launchables.model/-launchable/index.md))<br>Removes [item](reset-item-name.md)'s stored customization so it uses its default name again. |
| [setIconPack](set-icon-pack.md) | [main]<br>abstract fun [setIconPack](set-icon-pack.md)(iconPack: [IconPack](../../core.data.icons.model/-icon-pack/index.md)) |
| [setItemName](set-item-name.md) | [main]<br>abstract fun [setItemName](set-item-name.md)(item: [Launchable](../../core.data.launchables.model/-launchable/index.md), name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) |
