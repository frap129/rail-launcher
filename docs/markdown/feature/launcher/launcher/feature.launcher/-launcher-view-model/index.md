//[launcher](../../../index.md)/[feature.launcher](../index.md)/[LauncherViewModel](index.md)

# LauncherViewModel

[androidJvm]\
class [LauncherViewModel](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), appRepository: [AppRepository](../../../../../core/app-repo/app-repo/core.apprepo/-app-repository/index.md)) : [ViewModel](https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel.html)

## Constructors

| | |
|---|---|
| [LauncherViewModel](-launcher-view-model.md) | [androidJvm]<br>constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), appRepository: [AppRepository](../../../../../core/app-repo/app-repo/core.apprepo/-app-repository/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [launcherItems](launcher-items.md) | [androidJvm]<br>val [launcherItems](launcher-items.md): Flow&lt;[SortedMap](https://developer.android.com/reference/kotlin/java/util/SortedMap.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html), [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[App](../../../../../core/app-repo/app-repo/core.apprepo/-app/index.md)&gt;&gt;&gt; |

## Functions

| Name | Summary |
|---|---|
| [addCloseable](index.md#383812252%2FFunctions%2F1299190430) | [androidJvm]<br>open fun [addCloseable](index.md#383812252%2FFunctions%2F1299190430)(closeable: [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html))<br>fun [addCloseable](index.md#1722490497%2FFunctions%2F1299190430)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), closeable: [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html)) |
| [getCloseable](index.md#1102255800%2FFunctions%2F1299190430) | [androidJvm]<br>fun &lt;[T](index.md#1102255800%2FFunctions%2F1299190430) : [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html)&gt; [getCloseable](index.md#1102255800%2FFunctions%2F1299190430)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [T](index.md#1102255800%2FFunctions%2F1299190430)? |
