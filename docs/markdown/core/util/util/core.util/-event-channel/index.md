//[util](../../../index.md)/[core.util](../index.md)/[EventChannel](index.md)

# EventChannel

[main]\
class [EventChannel](index.md)&lt;[T](index.md)&gt;(capacity: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = Channel.BUFFERED)

A single-consumer event channel that queues events until a collector attaches.

## Constructors

| | |
|---|---|
| [EventChannel](-event-channel.md) | [main]<br>constructor(capacity: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = Channel.BUFFERED) |

## Properties

| Name | Summary |
|---|---|
| [flow](flow.md) | [main]<br>val [flow](flow.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[T](index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [send](send.md) | [main]<br>suspend fun [send](send.md)(event: [T](index.md))<br>Emits [event](send.md), suspending while the channel is full. |
| [trySend](try-send.md) | [main]<br>fun [trySend](try-send.md)(event: [T](index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Emits [event](try-send.md) without suspending; false when the channel is full. |
