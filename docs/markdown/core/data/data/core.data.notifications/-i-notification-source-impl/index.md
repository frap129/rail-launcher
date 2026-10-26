//[data](../../../index.md)/[core.data.notifications](../index.md)/[INotificationSourceImpl](index.md)

# INotificationSourceImpl

[main]\
class [INotificationSourceImpl](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, popupRepository: [IPopupRepository](../../core.data.popups/-i-popup-repository/index.md), relay: [INotificationListenerRelay](../-i-notification-listener-relay/index.md), isAccessGranted: (<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) -&gt; [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = ::isNotificationAccessGranted) : [INotificationSource](../-i-notification-source/index.md)

In-memory notification source backed by the listener Rail keeps connected.

## Constructors

| | |
|---|---|
| [INotificationSourceImpl](-i-notification-source-impl.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, popupRepository: [IPopupRepository](../../core.data.popups/-i-popup-repository/index.md), relay: [INotificationListenerRelay](../-i-notification-listener-relay/index.md), isAccessGranted: (<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) -&gt; [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = ::isNotificationAccessGranted) |

## Properties

| Name | Summary |
|---|---|
| [hasAccess](has-access.md) | [main]<br>open override val [hasAccess](has-access.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt;<br>Whether Rail's notification listener is enabled in system settings. |
| [notifications](notifications.md) | [main]<br>open override val [notifications](notifications.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationSnapshot](../-notification-snapshot/index.md)&gt;&gt;<br>The visible notifications, newest first; empty while access or the setting is missing. |

## Functions

| Name | Summary |
|---|---|
| [dismiss](dismiss.md) | [main]<br>open suspend override fun [dismiss](dismiss.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Cancels the clearable notification [key](dismiss.md). |
| [open](open.md) | [main]<br>open suspend override fun [open](open.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Sends [key](open.md)'s content intent and cancels it when its app asked for auto-cancel. |
| [refreshAccess](refresh-access.md) | [main]<br>open override fun [refreshAccess](refresh-access.md)() |
| [runAction](run-action.md) | [main]<br>open suspend override fun [runAction](run-action.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), replyText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Runs the action at [index](run-action.md) on [key](run-action.md), filling [replyText](run-action.md) into its RemoteInput when the action accepts one. Returns false when the action is missing or failed. |
