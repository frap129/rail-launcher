//[ui](../../../index.md)/[core.ui.model.data](../index.md)/[Destination](index.md)

# Destination

open class [Destination](index.md)(val route: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val content: @[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)([NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html), [NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html), val deepLinks: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NavDeepLink](https://developer.android.com/reference/kotlin/androidx/navigation/NavDeepLink.html)&gt; = emptyList(), val enterTransition: [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [EnterTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/EnterTransition.html)?? = {
        fadeIn(animationSpec = tween(ANIMATION_DURATION))
    }, val exitTransition: [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [ExitTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/ExitTransition.html)?? = {
        fadeOut(animationSpec = tween(ANIMATION_DURATION))
    }, val arguments: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NamedNavArgument](https://developer.android.com/reference/kotlin/androidx/navigation/NamedNavArgument.html)&gt; = emptyList(), val showNavBar: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, val navBarItem: [DestinationNavBarItem](../-destination-nav-bar-item/index.md)? = null)

A &quot;Destination&quot; represents a screen or flow that can be navigated to through the NavController. This class should include any data necessary for registering the destination in the NavHost, including resources needed for setting up a navigation bar.

#### See also

| |
|---|
| [DestinationNavBarItem](../-destination-nav-bar-item/index.md) |

## Constructors

| | |
|---|---|
| [Destination](-destination.md) | [androidJvm]<br>constructor(route: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), content: @[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)([NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html), [NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html), deepLinks: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NavDeepLink](https://developer.android.com/reference/kotlin/androidx/navigation/NavDeepLink.html)&gt; = emptyList(), enterTransition: [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [EnterTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/EnterTransition.html)?? = {         fadeIn(animationSpec = tween(ANIMATION_DURATION))     }, exitTransition: [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [ExitTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/ExitTransition.html)?? = {         fadeOut(animationSpec = tween(ANIMATION_DURATION))     }, arguments: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NamedNavArgument](https://developer.android.com/reference/kotlin/androidx/navigation/NamedNavArgument.html)&gt; = emptyList(), showNavBar: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, navBarItem: [DestinationNavBarItem](../-destination-nav-bar-item/index.md)? = null)<br>Creates a new destination object |

## Properties

| Name | Summary |
|---|---|
| [arguments](arguments.md) | [androidJvm]<br>open val [arguments](arguments.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NamedNavArgument](https://developer.android.com/reference/kotlin/androidx/navigation/NamedNavArgument.html)&gt;<br>list of [NamedNavArgument](https://developer.android.com/reference/kotlin/androidx/navigation/NamedNavArgument.html) supported by the destination |
| [content](content.md) | [androidJvm]<br>open val [content](content.md): @[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)([NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html), [NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)<br>Composable providing [NavController](https://developer.android.com/reference/kotlin/androidx/navigation/NavController.html) and [NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html) |
| [deepLinks](deep-links.md) | [androidJvm]<br>open val [deepLinks](deep-links.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NavDeepLink](https://developer.android.com/reference/kotlin/androidx/navigation/NavDeepLink.html)&gt;<br>list of [NavDeepLink](https://developer.android.com/reference/kotlin/androidx/navigation/NavDeepLink.html) that link to this destination |
| [enterTransition](enter-transition.md) | [androidJvm]<br>@[JvmSuppressWildcards](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-suppress-wildcards/index.html)<br>open val [enterTransition](enter-transition.md): [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [EnterTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/EnterTransition.html)??<br>animation to use when entering the destination |
| [exitTransition](exit-transition.md) | [androidJvm]<br>open val [exitTransition](exit-transition.md): [AnimatedContentTransitionScope](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedContentTransitionScope.html)&lt;[NavBackStackEntry](https://developer.android.com/reference/kotlin/androidx/navigation/NavBackStackEntry.html)&gt;.() -&gt; [ExitTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/ExitTransition.html)??<br>animation to use when exiting the destination |
| [navBarItem](nav-bar-item.md) | [androidJvm]<br>open val [navBarItem](nav-bar-item.md): [DestinationNavBarItem](../-destination-nav-bar-item/index.md)? = null<br>a [DestinationNavBarItem](../-destination-nav-bar-item/index.md) that links to this destination |
| [route](route.md) | [androidJvm]<br>open val [route](route.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>name of the route |
| [showNavBar](show-nav-bar.md) | [androidJvm]<br>open val [showNavBar](show-nav-bar.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false<br>whether a navbar should be shown at the destination |