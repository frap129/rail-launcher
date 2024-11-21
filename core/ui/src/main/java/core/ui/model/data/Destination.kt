package core.ui.model.data

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import core.ui.theme.ANIMATION_DURATION

/**
 * A "Destination" represents a screen or flow that can be navigated to
 * through the NavController. This class should include any data necessary
 * for registering the destination in the NavHost, including resources
 * needed for setting up a navigation bar.
 *
 * @property route name of the route
 * @property content Composable providing [NavController] and [NavBackStackEntry]
 * @property deepLinks list of [NavDeepLink] that link to this destination
 * @property enterTransition animation to use when entering the destination
 * @property exitTransition animation to use when exiting the destination
 * @property arguments list of [NamedNavArgument] supported by the destination
 * @constructor Creates a new destination object
 */
class Destination(
    val route: String,
    val content: @Composable (NavController, NavBackStackEntry) -> Unit,
    val deepLinks: List<NavDeepLink> = emptyList(),
    @Suppress("ktlint:standard:max-line-length")
    @JvmSuppressWildcards()
    val enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = {
        fadeIn(animationSpec = tween(ANIMATION_DURATION))
    },
    @Suppress("ktlint:standard:max-line-length")
    val exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = {
        fadeOut(animationSpec = tween(ANIMATION_DURATION))
    },
    val arguments: List<NamedNavArgument> = emptyList(),
)
