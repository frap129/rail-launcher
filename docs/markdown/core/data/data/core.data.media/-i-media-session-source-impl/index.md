//[data](../../../index.md)/[core.data.media](../index.md)/[IMediaSessionSourceImpl](index.md)

# IMediaSessionSourceImpl

[main]\
class [IMediaSessionSourceImpl](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, artworkStore: [MediaArtworkStore](../-media-artwork-store/index.md)) : [IMediaSessionSource](../-i-media-session-source/index.md)

## Constructors

| | |
|---|---|
| [IMediaSessionSourceImpl](-i-media-session-source-impl.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, artworkStore: [MediaArtworkStore](../-media-artwork-store/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [hasAccess](has-access.md) | [main]<br>open override val [hasAccess](has-access.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt;<br>Whether Rail's notification listener can currently list and control MediaSessions. |
| [sessions](sessions.md) | [main]<br>open override val [sessions](sessions.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[MediaSessionSnapshot](../-media-session-snapshot/index.md)&gt;&gt;<br>Active sessions in the system's ordering, while notification access is granted. |

## Functions

| Name | Summary |
|---|---|
| [execute](execute.md) | [main]<br>open override fun [execute](execute.md)(key: [MediaSessionKey](../-media-session-key/index.md), command: [MediaCommand](../-media-command/index.md)) |
| [open](open.md) | [main]<br>open override fun [open](open.md)(key: [MediaSessionKey](../-media-session-key/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Opens the player's own UI. Returns false when no activity could be started. |
| [refreshAccess](refresh-access.md) | [main]<br>open override fun [refreshAccess](refresh-access.md)() |
