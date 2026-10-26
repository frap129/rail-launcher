//[data](../../../index.md)/[core.data.folders](../index.md)/[IFolderRepositoryImpl](index.md)

# IFolderRepositoryImpl

[main]\
class [IFolderRepositoryImpl](index.md)(folderDao: [FolderDao](../../core.data.room.dao/-folder-dao/index.md), favoritesRepository: [IFavoritesRepository](../../core.data.favorites/-i-favorites-repository/index.md), popupRepository: [IPopupRepository](../../core.data.popups/-i-popup-repository/index.md), preferencesRepository: [IPreferencesRepository](../../core.data.prefs/-i-preferences-repository/index.md)) : [IFolderRepository](../-i-folder-repository/index.md)

## Constructors

| | |
|---|---|
| [IFolderRepositoryImpl](-i-folder-repository-impl.md) | [main]<br>constructor(folderDao: [FolderDao](../../core.data.room.dao/-folder-dao/index.md), favoritesRepository: [IFavoritesRepository](../../core.data.favorites/-i-favorites-repository/index.md), popupRepository: [IPopupRepository](../../core.data.popups/-i-popup-repository/index.md), preferencesRepository: [IPreferencesRepository](../../core.data.prefs/-i-preferences-repository/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [folders](folders.md) | [main]<br>open override val [folders](folders.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Folder](../-folder/index.md)&gt;&gt;<br>Every Folder, with its resolved name. |

## Functions

| Name | Summary |
|---|---|
| [create](create.md) | [main]<br>open suspend override fun [create](create.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, initialItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;, addToFavorites: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [Folder](../-folder/index.md)<br>Creates a Folder, stores [name](create.md) as its customization when it is not blank or the default, adds [initialItems](create.md) to its Pop-up and, when [addToFavorites](create.md) is true, appends it to the Favorites. |
| [delete](delete.md) | [main]<br>open suspend override fun [delete](delete.md)(folder: [Folder](../-folder/index.md)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[AppWidgetId](../../core.data.widgets/-app-widget-id/index.md)&gt;<br>Deletes [folder](delete.md) and everything belonging to it, returning the App Widget ids it held. |
| [setShowInAppList](set-show-in-app-list.md) | [main]<br>open suspend override fun [setShowInAppList](set-show-in-app-list.md)(folder: [Folder](../-folder/index.md), show: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))<br>Sets whether [folder](set-show-in-app-list.md) is shown in the App List. |
