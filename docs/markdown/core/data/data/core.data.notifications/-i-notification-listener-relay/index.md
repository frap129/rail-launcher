//[data](../../../index.md)/[core.data.notifications](../index.md)/[INotificationListenerRelay](index.md)

# INotificationListenerRelay

interface [INotificationListenerRelay](index.md)

Keeps the notifications reported by the listener and the actions Rail can run on them.

#### Inheritors

| |
|---|
| [INotificationListenerRelayImpl](../-i-notification-listener-relay-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [records](records.md) | [main]<br>abstract val [records](records.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationRecord](../-notification-record/index.md)&gt;&gt;<br>The current records. |

## Functions

| Name | Summary |
|---|---|
| [attach](attach.md) | [main]<br>abstract fun [attach](attach.md)(service: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [cancel](cancel.md) | [main]<br>abstract fun [cancel](cancel.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Asks the system to cancel [key](cancel.md). |
| [clear](clear.md) | [main]<br>abstract fun [clear](clear.md)() |
| [detach](detach.md) | [main]<br>abstract fun [detach](detach.md)(service: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [handle](handle.md) | [main]<br>abstract fun [handle](handle.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [NotificationHandle](../-notification-handle/index.md)? |
| [post](post.md) | [main]<br>abstract fun [post](post.md)(record: [NotificationRecord](../-notification-record/index.md), handle: [NotificationHandle](../-notification-handle/index.md)) |
| [record](record.md) | [main]<br>abstract fun [record](record.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [NotificationRecord](../-notification-record/index.md)? |
| [refreshFromService](refresh-from-service.md) | [main]<br>abstract fun [refreshFromService](refresh-from-service.md)()<br>Rebuilds the records from the notifications the system still reports. |
| [remove](remove.md) | [main]<br>abstract fun [remove](remove.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) |
| [setCollecting](set-collecting.md) | [main]<br>abstract fun [setCollecting](set-collecting.md)(collecting: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))<br>Starts or stops keeping reported notifications. |
