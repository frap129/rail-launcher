//[data](../../../index.md)/[core.data.folders](../index.md)/[IFolderRepository](index.md)

# IFolderRepository

interface [IFolderRepository](index.md)

#### Inheritors

| |
|---|
| [IFolderRepositoryImpl](../-i-folder-repository-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [folders](folders.md) | [main]<br>abstract val [folders](folders.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Folder](../-folder/index.md)&gt;&gt;<br>Every Folder, with its resolved name. |

## Functions

| Name | Summary |
|---|---|
| [create](create.md) | [main]<br>abstract suspend fun [create](create.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, initialItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptyList(), addToFavorites: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true): [Folder](../-folder/index.md)<br>Creates a Folder, stores [name](create.md) as its customization when it is not blank or the default, adds [initialItems](create.md) to its Pop-up and, when [addToFavorites](create.md) is true, appends it to the Favorites. |
| [delete](delete.md) | [main]<br>abstract suspend fun [delete](delete.md)(folder: [Folder](../-folder/index.md)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[AppWidgetId](../../core.data.widgets/-app-widget-id/index.md)&gt;<br>Deletes [folder](delete.md) and everything belonging to it, returning the App Widget ids it held. |
| [setShowInAppList](set-show-in-app-list.md) | [main]<br>abstract suspend fun [setShowInAppList](set-show-in-app-list.md)(folder: [Folder](../-folder/index.md), show: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))<br>Sets whether [folder](set-show-in-app-list.md) is shown in the App List. |
