//[launcher](../../../index.md)/[feature.launcher](../index.md)/[LauncherViewModel](index.md)

# LauncherViewModel

[androidJvm]\
class [LauncherViewModel](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), launcherItemRepo: [LauncherItemRepository](../../../../../core/data/data/core.data.launcher/-launcher-item-repository/index.md), prefsRepo: [PreferencesRepository](../../../../../core/data/data/core.data.prefs/-preferences-repository/index.md)) : [ViewModel](https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel.html)

## Constructors

| | |
|---|---|
| [LauncherViewModel](-launcher-view-model.md) | [androidJvm]<br>constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), launcherItemRepo: [LauncherItemRepository](../../../../../core/data/data/core.data.launcher/-launcher-item-repository/index.md), prefsRepo: [PreferencesRepository](../../../../../core/data/data/core.data.prefs/-preferences-repository/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [launcherItemGroups](launcher-item-groups.md) | [androidJvm]<br>val [launcherItemGroups](launcher-item-groups.md): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LauncherItemGroup](../../../../../core/data/data/core.data.launcher.model/-launcher-item-group/index.md)&gt;&gt; |
| [listEndOffsetPx](list-end-offset-px.md) | [androidJvm]<br>val [listEndOffsetPx](list-end-offset-px.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [listStartOffsetPx](list-start-offset-px.md) | [androidJvm]<br>val [listStartOffsetPx](list-start-offset-px.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [menuState](menu-state.md) | [androidJvm]<br>val [menuState](menu-state.md): StateFlow&lt;[LauncherMenuState](../-launcher-menu-state/index.md)&gt; |
| [uiState](ui-state.md) | [androidJvm]<br>val [uiState](ui-state.md): StateFlow&lt;[LauncherUiState](../-launcher-ui-state/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [addCloseable](index.md#383812252%2FFunctions%2F1299190430) | [androidJvm]<br>open fun [addCloseable](index.md#383812252%2FFunctions%2F1299190430)(closeable: [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html))<br>fun [addCloseable](index.md#1722490497%2FFunctions%2F1299190430)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), closeable: [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html)) |
| [closeItemMenu](close-item-menu.md) | [androidJvm]<br>fun [closeItemMenu](close-item-menu.md)() |
| [getCloseable](index.md#1102255800%2FFunctions%2F1299190430) | [androidJvm]<br>fun &lt;[T](index.md#1102255800%2FFunctions%2F1299190430) : [AutoCloseable](https://developer.android.com/reference/kotlin/java/lang/AutoCloseable.html)&gt; [getCloseable](index.md#1102255800%2FFunctions%2F1299190430)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [T](index.md#1102255800%2FFunctions%2F1299190430)? |
| [onScroll](on-scroll.md) | [androidJvm]<br>fun [onScroll](on-scroll.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [onScrollEnded](on-scroll-ended.md) | [androidJvm]<br>fun [onScrollEnded](on-scroll-ended.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [onScrollStarted](on-scroll-started.md) | [androidJvm]<br>fun [onScrollStarted](on-scroll-started.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [openItemMenu](open-item-menu.md) | [androidJvm]<br>fun [openItemMenu](open-item-menu.md)(launcherItem: [LauncherItem](../../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md)) |
| [openItemRename](open-item-rename.md) | [androidJvm]<br>fun [openItemRename](open-item-rename.md)(launcherItem: [LauncherItem](../../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md)) |
| [setItemName](set-item-name.md) | [androidJvm]<br>fun [setItemName](set-item-name.md)(launcherItem: [LauncherItem](../../../../../core/data/data/core.data.launcher.model/-launcher-item/index.md), name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
