//[data](../../../index.md)/[core.data.notifications](../index.md)/[INotificationSourceImpl](index.md)/[runAction](run-action.md)

# runAction

[main]\
open suspend override fun [runAction](run-action.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), replyText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)

Runs the action at [index](run-action.md) on [key](run-action.md), filling [replyText](run-action.md) into its RemoteInput when the action accepts one. Returns false when the action is missing or failed.
