package core.ui

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import core.ui.model.data.Destination
import core.ui.model.data.LauncherSignal
import core.ui.model.data.LauncherSignalRequest
import core.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

private const val DARK_SURFACE_LUMINANCE_THRESHOLD: Float = 0.5f

/** Returns to [startDestination], navigating to it when nothing was above it. */
private fun showStartDestination(navController: NavController, startDestination: String) {
    val startOnBackStack: Boolean = navController.popBackStack(startDestination, inclusive = false)
    if (!startOnBackStack) {
        navController.navigate(startDestination)
    }
}

/*
 * This is the root composable for this application. It creates a NavHost
 * within an AppFrame, allowing any screen or flow that is in the
 * `destinations` list to be accessed.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalComposeUiApi::class, KoinExperimentalAPI::class)
@Composable
fun App(
    destinations: List<Destination>,
    startDestination: String,
    launcherSignals: Flow<LauncherSignal>,
    onDestinationChanged: (route: String?, isWallpaperBacked: Boolean, isSurfaceDark: Boolean) -> Unit
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val latestBackStackEntry: NavBackStackEntry? by rememberUpdatedState(currentBackStackEntry)
    val isWallpaperBacked: Boolean = currentBackStackEntry?.destination?.route
        ?.let { route -> destinations.find { destination -> destination.route == route } }
        ?.isWallpaperBacked ?: false
    val currentOnDestinationChanged by rememberUpdatedState(onDestinationChanged)

    LaunchedEffect(launcherSignals) {
        launcherSignals.collect { signal ->
            val wasShowingStart: Boolean = latestBackStackEntry?.destination?.route == startDestination
            if (signal !is LauncherSignal.TypedCharacter) {
                showStartDestination(navController = navController, startDestination = startDestination)
            }
            val signalForLauncher: LauncherSignal = if (signal is LauncherSignal.Home) {
                signal.copy(wasResumed = signal.wasResumed && wasShowingStart)
            } else {
                signal
            }
            LauncherSignalRequest(
                savedStateHandle = navController.getBackStackEntry(startDestination).savedStateHandle
            ).request(signal = signalForLauncher)
        }
    }

    AppTheme {
        val isSurfaceDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD
        val currentRoute: String? = currentBackStackEntry?.destination?.route
        LaunchedEffect(currentBackStackEntry, isSurfaceDark) {
            if (currentBackStackEntry != null) {
                currentOnDestinationChanged(currentRoute, isWallpaperBacked, isSurfaceDark)
            }
        }

        KoinAndroidContext {
            NavHost(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                navController = navController,
                startDestination = startDestination
            ) {
                // Add all defined destinations to the NavGraph
                destinations.forEach { dest ->
                    composable(
                        route = dest.route,
                        content = { navBackStackEntry ->
                            dest.content(navController, navBackStackEntry)
                        },
                        deepLinks = dest.deepLinks,
                        arguments = dest.arguments,
                        enterTransition = dest.enterTransition,
                        exitTransition = dest.exitTransition
                    )
                }
            }
        }
    }
}
