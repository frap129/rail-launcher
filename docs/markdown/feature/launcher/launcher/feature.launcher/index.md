//[launcher](../../index.md)/[feature.launcher](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [LauncherMenuState](-launcher-menu-state/index.md) | [androidJvm]<br>sealed class [LauncherMenuState](-launcher-menu-state/index.md) |
| [LauncherUiState](-launcher-ui-state/index.md) | [androidJvm]<br>sealed class [LauncherUiState](-launcher-ui-state/index.md) |
| [LauncherViewModel](-launcher-view-model/index.md) | [androidJvm]<br>class [LauncherViewModel](-launcher-view-model/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), launcherItemRepo: [LauncherItemRepository](../../../../core/data/data/core.data.launcher/-launcher-item-repository/index.md), prefsRepo: [PreferencesRepository](../../../../core/data/data/core.data.prefs/-preferences-repository/index.md)) : [ViewModel](https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel.html) |

## Properties

| Name | Summary |
|---|---|
| [launcherDestination](launcher-destination.md) | [androidJvm]<br>val [launcherDestination](launcher-destination.md): [Destination](../../../../core/ui/ui/core.ui.model.data/-destination/index.md) |

## Functions

| Name | Summary |
|---|---|
| [LauncherItem](-launcher-item.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherItem](-launcher-item.md)(item: [LauncherItem](../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md), viewModel: [LauncherViewModel](-launcher-view-model/index.md) = koinViewModel()) |
| [LauncherItemEditName](-launcher-item-edit-name.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherItemEditName](-launcher-item-edit-name.md)(item: [LauncherItem](../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md), viewModel: [LauncherViewModel](-launcher-view-model/index.md) = koinViewModel()) |
| [LauncherItemGroup](-launcher-item-group.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherItemGroup](-launcher-item-group.md)(modifier: [Modifier](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.html) = Modifier, group: [LauncherItemGroup](../../../../core/data/data/core.data.launcher.model/-launcher-item-group/index.md)) |
| [LauncherItemMenu](-launcher-item-menu.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherItemMenu](-launcher-item-menu.md)(item: [LauncherItem](../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md), viewModel: [LauncherViewModel](-launcher-view-model/index.md) = koinViewModel()) |
| [LauncherList](-launcher-list.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherList](-launcher-list.md)(modifier: [Modifier](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.html) = Modifier, launcherList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LauncherItemGroup](../../../../core/data/data/core.data.launcher.model/-launcher-item-group/index.md)&gt;, lazyListState: [LazyListState](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState.html), topOffset: [Dp](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Dp.html), bottomOffset: [Dp](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Dp.html)) |
| [LauncherScreen](-launcher-screen.md) | [androidJvm]<br>@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)<br>fun [LauncherScreen](-launcher-screen.md)(navController: [NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html), viewModel: [LauncherViewModel](-launcher-view-model/index.md) = koinViewModel()) |
