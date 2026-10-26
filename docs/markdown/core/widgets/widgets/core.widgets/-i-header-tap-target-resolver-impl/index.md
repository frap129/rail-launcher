//[widgets](../../../index.md)/[core.widgets](../index.md)/[IHeaderTapTargetResolverImpl](index.md)

# IHeaderTapTargetResolverImpl

[main]\
class [IHeaderTapTargetResolverImpl](index.md) : [IHeaderTapTargetResolver](../-i-header-tap-target-resolver/index.md)

## Constructors

| | |
|---|---|
| [IHeaderTapTargetResolverImpl](-i-header-tap-target-resolver-impl.md) | [main]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [applyTo](apply-to.md) | [main]<br>open override fun [applyTo](apply-to.md)(header: [HeaderModel](../-header-model/index.md), settings: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, launchables: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, atEpochMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)): [HeaderModel](../-header-model/index.md)<br>Rewrites a formatted Header's tap targets from [settings](apply-to.md). |
| [resolveSetting](resolve-setting.md) | [main]<br>open override fun [resolveSetting](resolve-setting.md)(setting: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, launchables: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, isLoaded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---><br>Resolves a stored Tap action for display: a Launchable that no longer exists falls back to the system default once Launchables have loaded. |
