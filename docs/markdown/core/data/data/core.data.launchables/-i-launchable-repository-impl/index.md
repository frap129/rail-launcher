//[data](../../../index.md)/[core.data.launchables](../index.md)/[ILaunchableRepositoryImpl](index.md)

# ILaunchableRepositoryImpl

[main]\
class [ILaunchableRepositoryImpl](index.md)(appRepository: [IAppRepository](../../core.data.apps/-i-app-repository/index.md), iconRepository: [IIconRepository](../../core.data.icons/-i-icon-repository/index.md), preferencesRepository: [IPreferencesRepository](../../core.data.prefs/-i-preferences-repository/index.md), folderDao: [FolderDao](../../core.data.room.dao/-folder-dao/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [ILaunchableRepository](../-i-launchable-repository/index.md)

## Constructors

| | |
|---|---|
| [ILaunchableRepositoryImpl](-i-launchable-repository-impl.md) | [main]<br>constructor(appRepository: [IAppRepository](../../core.data.apps/-i-app-repository/index.md), iconRepository: [IIconRepository](../../core.data.icons/-i-icon-repository/index.md), preferencesRepository: [IPreferencesRepository](../../core.data.prefs/-i-preferences-repository/index.md), folderDao: [FolderDao](../../core.data.room.dao/-folder-dao/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |

## Properties

| Name | Summary |
|---|---|
| [launchables](launchables.md) | [main]<br>open override val [launchables](launchables.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;&gt;<br>Every Launchable, including apps, Rail entries and Folders, with customizations applied. |
| [launchableState](launchable-state.md) | [main]<br>open override val [launchableState](launchable-state.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[LaunchableState](../-launchable-state/index.md)&gt;<br>The shared Launchable State, computed off the main thread. Starts with [LaunchableState.isLoaded](../-launchable-state/is-loaded.md) false and keeps its upstream subscription for WHILE_SUBSCRIBED_TIMEOUT_MS after the last collector leaves. |
