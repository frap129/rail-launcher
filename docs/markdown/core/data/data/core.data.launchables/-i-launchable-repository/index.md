//[data](../../../index.md)/[core.data.launchables](../index.md)/[ILaunchableRepository](index.md)

# ILaunchableRepository

interface [ILaunchableRepository](index.md)

#### Inheritors

| |
|---|
| [ILaunchableRepositoryImpl](../-i-launchable-repository-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [launchables](launchables.md) | [main]<br>abstract val [launchables](launchables.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;&gt;<br>Every Launchable, including apps, Rail entries and Folders, with customizations applied. |
| [launchableState](launchable-state.md) | [main]<br>abstract val [launchableState](launchable-state.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[LaunchableState](../-launchable-state/index.md)&gt;<br>The shared Launchable State, computed off the main thread. Starts with [LaunchableState.isLoaded](../-launchable-state/is-loaded.md) false and keeps its upstream subscription for WHILE_SUBSCRIBED_TIMEOUT_MS after the last collector leaves. |
