//[data](../../../index.md)/[core.data.notifications](../index.md)/[INotificationSource](index.md)

# INotificationSource

interface [INotificationSource](index.md)

The only data source that reads system notifications. Nothing is persisted and content intents stay inside the implementation.

#### Inheritors

| |
|---|
| [INotificationSourceImpl](../-i-notification-source-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [hasAccess](has-access.md) | [main]<br>abstract val [hasAccess](has-access.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt;<br>Whether Rail's notification listener is enabled in system settings. |
| [notifications](notifications.md) | [main]<br>abstract val [notifications](notifications.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationSnapshot](../-notification-snapshot/index.md)&gt;&gt;<br>The visible notifications, newest first; empty while access or the setting is missing. |

## Functions

| Name | Summary |
|---|---|
| [dismiss](dismiss.md) | [main]<br>abstract suspend fun [dismiss](dismiss.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Cancels the clearable notification [key](dismiss.md). |
| [open](open.md) | [main]<br>abstract suspend fun [open](open.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Sends [key](open.md)'s content intent and cancels it when its app asked for auto-cancel. |
| [refreshAccess](refresh-access.md) | [main]<br>abstract fun [refreshAccess](refresh-access.md)() |
| [runAction](run-action.md) | [main]<br>abstract suspend fun [runAction](run-action.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), replyText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Runs the action at [index](run-action.md) on [key](run-action.md), filling [replyText](run-action.md) into its RemoteInput when the action accepts one. Returns false when the action is missing or failed. |
