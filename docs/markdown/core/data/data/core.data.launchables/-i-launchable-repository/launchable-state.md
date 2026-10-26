//[data](../../../index.md)/[core.data.launchables](../index.md)/[ILaunchableRepository](index.md)/[launchableState](launchable-state.md)

# launchableState

[main]\
abstract val [launchableState](launchable-state.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[LaunchableState](../-launchable-state/index.md)&gt;

The shared Launchable State, computed off the main thread. Starts with [LaunchableState.isLoaded](../-launchable-state/is-loaded.md) false and keeps its upstream subscription for WHILE_SUBSCRIBED_TIMEOUT_MS after the last collector leaves.
