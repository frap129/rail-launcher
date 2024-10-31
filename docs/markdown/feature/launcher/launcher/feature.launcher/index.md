//[launcher](../../index.md)/[feature.launcher](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [LauncherScrollRailHelper](-launcher-scroll-rail-helper/index.md) | [androidJvm]<br>class [LauncherScrollRailHelper](-launcher-scroll-rail-helper/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), val railItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html)&gt;, listState: [LazyListState](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState.html), visibilities: [MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html), [MutableState](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableState.html)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;&gt;) : [ScrollRailHelper](../../../../core/ui/ui/core.ui.composables.scrollrail/-scroll-rail-helper/index.md) |
| [LauncherViewModel](-launcher-view-model/index.md) | [androidJvm]<br>class [LauncherViewModel](-launcher-view-model/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), appRepository: [AppRepository](../../../../core/app-repo/app-repo/core.apprepo/-app-repository/index.md)) : [ViewModel](https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel.html) |

## Properties

| Name | Summary |
|---|---|
| [launcherDestination](launcher-destination.md) | [androidJvm]<br>val [launcherDestination](launcher-destination.md): [Destination](../../../../core/ui/ui/core.ui.model.data/-destination/index.md) |

## Functions

| Name | Summary |
|---|---|
| [itemGroup](item-group.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [itemGroup](item-group.md)(label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[App](../../../../core/app-repo/app-repo/core.apprepo/-app/index.md)&gt;): [MutableState](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableState.html)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt; |
| [LauncherScreen](-launcher-screen.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherScreen](-launcher-screen.md)(navController: [NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html), viewModel: [LauncherViewModel](-launcher-view-model/index.md) = koinViewModel()) |
