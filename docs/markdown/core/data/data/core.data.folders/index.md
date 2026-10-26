//[data](../../index.md)/[core.data.folders](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Folder](-folder/index.md) | [main]<br>data class [Folder](-folder/index.md)(val folderId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val showInAppList: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = DEFAULT_NAME, val icon: [Icon](../core.data.icons.model/-icon/index.md) = Icon.FolderIcon(shade = folderShade(folderId))) : [Launchable](../core.data.launchables.model/-launchable/index.md)<br>A Launchable that only opens a Pop-up listing the Launchables the user put in it. |
| [FolderRecord](-folder-record/index.md) | [main]<br>data class [FolderRecord](-folder-record/index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) = 0, val showInAppList: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false)<br>A stored Folder. Its name and icon are resolved through the Launchable customizations. |
| [IFolderRepository](-i-folder-repository/index.md) | [main]<br>interface [IFolderRepository](-i-folder-repository/index.md) |
| [IFolderRepositoryImpl](-i-folder-repository-impl/index.md) | [main]<br>class [IFolderRepositoryImpl](-i-folder-repository-impl/index.md)(folderDao: [FolderDao](../core.data.room.dao/-folder-dao/index.md), favoritesRepository: [IFavoritesRepository](../core.data.favorites/-i-favorites-repository/index.md), popupRepository: [IPopupRepository](../core.data.popups/-i-popup-repository/index.md), preferencesRepository: [IPreferencesRepository](../core.data.prefs/-i-preferences-repository/index.md)) : [IFolderRepository](-i-folder-repository/index.md) |
