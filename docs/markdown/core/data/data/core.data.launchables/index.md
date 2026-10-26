//[data](../../index.md)/[core.data.launchables](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [ILaunchableRepository](-i-launchable-repository/index.md) | [main]<br>interface [ILaunchableRepository](-i-launchable-repository/index.md) |
| [ILaunchableRepositoryImpl](-i-launchable-repository-impl/index.md) | [main]<br>class [ILaunchableRepositoryImpl](-i-launchable-repository-impl/index.md)(appRepository: [IAppRepository](../core.data.apps/-i-app-repository/index.md), iconRepository: [IIconRepository](../core.data.icons/-i-icon-repository/index.md), preferencesRepository: [IPreferencesRepository](../core.data.prefs/-i-preferences-repository/index.md), folderDao: [FolderDao](../core.data.room.dao/-folder-dao/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [ILaunchableRepository](-i-launchable-repository/index.md) |
| [LaunchableState](-launchable-state/index.md) | [main]<br>data class [LaunchableState](-launchable-state/index.md)(val groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../core.data.launchables.model/-launchable-group/index.md)&gt;, val launchables: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../core.data.launchables.model/-launchable/index.md)&gt;, val isLoaded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))<br>The Rail groups, the App List groups, every Launchable, and whether the installed apps have loaded. |

## Functions

| Name | Summary |
|---|---|
| [toAppListLaunchables](to-app-list-launchables.md) | [main]<br>fun [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../core.data.launchables.model/-launchable-group/index.md)&gt;.[toAppListLaunchables](to-app-list-launchables.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../core.data.launchables.model/-launchable/index.md)&gt;<br>Flattens the App List groups into their Launchables in Rail order. The Folders group is skipped because every Folder is flat-listed from its record, whether or not that group shows it. |
