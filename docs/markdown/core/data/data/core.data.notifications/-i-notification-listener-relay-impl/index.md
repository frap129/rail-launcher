//[data](../../../index.md)/[core.data.notifications](../index.md)/[INotificationListenerRelayImpl](index.md)

# INotificationListenerRelayImpl

[main]\
class [INotificationListenerRelayImpl](index.md) : [INotificationListenerRelay](../-i-notification-listener-relay/index.md)

In-memory holder for what [RailNotificationListener](../../core.data.media/-rail-notification-listener/index.md) reports.

## Constructors

| | |
|---|---|
| [INotificationListenerRelayImpl](-i-notification-listener-relay-impl.md) | [main]<br>constructor() |

## Properties

| Name | Summary |
|---|---|
| [records](records.md) | [main]<br>open override val [records](records.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationRecord](../-notification-record/index.md)&gt;&gt;<br>The current records. |

## Functions

| Name | Summary |
|---|---|
| [attach](attach.md) | [main]<br>open override fun [attach](attach.md)(service: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [cancel](cancel.md) | [main]<br>open override fun [cancel](cancel.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>Asks the system to cancel [key](cancel.md). |
| [clear](clear.md) | [main]<br>open override fun [clear](clear.md)() |
| [detach](detach.md) | [main]<br>open override fun [detach](detach.md)(service: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [handle](handle.md) | [main]<br>open override fun [handle](handle.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [NotificationHandle](../-notification-handle/index.md)? |
| [post](post.md) | [main]<br>open override fun [post](post.md)(record: [NotificationRecord](../-notification-record/index.md), handle: [NotificationHandle](../-notification-handle/index.md)) |
| [record](record.md) | [main]<br>open override fun [record](record.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [NotificationRecord](../-notification-record/index.md)? |
| [refreshFromService](refresh-from-service.md) | [main]<br>open override fun [refreshFromService](refresh-from-service.md)()<br>Rebuilds the records from the notifications the system still reports. |
| [remove](remove.md) | [main]<br>open override fun [remove](remove.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) |
| [setCollecting](set-collecting.md) | [main]<br>open override fun [setCollecting](set-collecting.md)(collecting: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))<br>Starts or stops keeping reported notifications. |
