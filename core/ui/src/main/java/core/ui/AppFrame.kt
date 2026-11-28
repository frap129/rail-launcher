package core.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import core.data.icons.IHomeIconCache
import core.data.prefs.IPreferencesRepository
import core.data.prefs.Settings
import core.ui.composables.LocalHomeIconCache
import core.ui.composables.LocalLauncherInputState
import core.ui.composables.synchronizeRowAccessibilityFocus
import core.ui.model.data.Destination
import core.ui.model.data.LauncherInputState
import core.ui.model.data.LauncherSignal
import core.ui.model.data.LauncherSignalRequest
import core.ui.model.data.narrowSearchToStartDestination
import core.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI

private const val DARK_SURFACE_LUMINANCE_THRESHOLD: Float = 0.5f

/** Resolves Home or onboarding from the stored completion preference. */
internal fun resolveStartDestination(isOnboardingCompleted: Boolean, onboardingRoute: String, homeRoute: String): String =
    if (isOnboardingCompleted) homeRoute else onboardingRoute

/**
 * Returns to [route], navigating to it when it is not on the back stack. While [route] is already
 * shown nothing navigates, so its entry is never replaced, keeping its ViewModels and their state.
 * [popUpToRoute] removes the flow that is finishing when [route] is not already on the back stack.
 */
fun showDestination(navController: NavController, route: String, popUpToRoute: String? = null) {
    val currentRoute: String? = navController.currentBackStackEntry?.destination?.route
    if (currentRoute == route) {
        return
    }
    val routeOnBackStack: Boolean = navController.popBackStack(route, inclusive = false)
    if (!routeOnBackStack) {
        navController.navigate(route) {
            if (popUpToRoute != null) {
                popUpTo(popUpToRoute) { inclusive = true }
            }
            launchSingleTop = true
        }
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
    onDestinationChanged: (route: String?, isWallpaperBacked: Boolean, isSurfaceDark: Boolean) -> Unit,
    onboardingRoute: String
) {
    val navController: NavHostController = rememberNavController()
    val currentBackStackEntry: NavBackStackEntry? by navController.currentBackStackEntryAsState()
    val latestBackStackEntry: State<NavBackStackEntry?> = rememberUpdatedState(currentBackStackEntry)
    val isWallpaperBacked: Boolean = currentBackStackEntry?.destination?.route
        ?.let { route -> destinations.find { destination -> destination.route == route } }
        ?.isWallpaperBacked ?: false
    val currentOnDestinationChanged by rememberUpdatedState(onDestinationChanged)
    AppTheme {
        val isSurfaceDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD
        val currentRoute: String? = currentBackStackEntry?.destination?.route
        LaunchedEffect(currentBackStackEntry, isSurfaceDark) {
            if (currentBackStackEntry != null) {
                currentOnDestinationChanged(currentRoute, isWallpaperBacked, isSurfaceDark)
            }
        }
        KoinAndroidContext {
            val preferencesRepository: IPreferencesRepository = koinInject()
            val graphStartDestination: String? by produceState<String?>(
                initialValue = null,
                preferencesRepository,
                startDestination,
                onboardingRoute
            ) {
                value = resolveStartDestination(
                    isOnboardingCompleted = preferencesRepository.settings.first().onboardingCompleted,
                    onboardingRoute = onboardingRoute,
                    homeRoute = startDestination
                )
            }
            val resolvedStartDestination: String? = graphStartDestination
            if (resolvedStartDestination == null) {
                Surface(modifier = Modifier.fillMaxSize()) {}
            } else {
                AppNavHost(
                    destinations = destinations,
                    navController = navController,
                    startDestination = resolvedStartDestination,
                    homeRoute = startDestination,
                    onboardingRoute = onboardingRoute,
                    preferencesRepository = preferencesRepository,
                    latestBackStackEntry = latestBackStackEntry,
                    launcherSignals = launcherSignals
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, KoinExperimentalAPI::class)
@Composable
private fun AppNavHost(
    destinations: List<Destination>,
    navController: NavHostController,
    startDestination: String,
    homeRoute: String,
    onboardingRoute: String,
    preferencesRepository: IPreferencesRepository,
    latestBackStackEntry: State<NavBackStackEntry?>,
    launcherSignals: Flow<LauncherSignal>
) {
    val homeIconCache: IHomeIconCache = koinInject()
    val launcherInputState: LauncherInputState = koinInject()
    CompositionLocalProvider(
        LocalHomeIconCache provides homeIconCache,
        LocalLauncherInputState provides launcherInputState
    ) {
        synchronizeRowAccessibilityFocus {
            NavHost(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                navController = navController,
                startDestination = startDestination
            ) {
                destinations.forEach { destination: Destination ->
                    composable(
                        route = destination.route,
                        content = { navBackStackEntry ->
                            destination.content(navController, navBackStackEntry)
                        },
                        deepLinks = destination.deepLinks,
                        arguments = destination.arguments,
                        enterTransition = destination.enterTransition,
                        exitTransition = destination.exitTransition
                    )
                }
            }
            LauncherSignalEffect(
                navController = navController,
                homeRoute = homeRoute,
                onboardingRoute = onboardingRoute,
                preferencesRepository = preferencesRepository,
                latestBackStackEntry = latestBackStackEntry,
                launcherSignals = launcherSignals
            )
        }
    }
}

/** Incomplete onboarding stays ahead of launcher signals until Skip or Finish persists completion. */
internal fun shouldKeepOnboarding(currentRoute: String?, onboardingRoute: String, isOnboardingCompleted: Boolean?): Boolean =
    currentRoute == onboardingRoute && isOnboardingCompleted != true

@Composable
internal fun LauncherSignalEffect(
    navController: NavHostController,
    homeRoute: String,
    onboardingRoute: String,
    preferencesRepository: IPreferencesRepository,
    latestBackStackEntry: State<NavBackStackEntry?>,
    launcherSignals: Flow<LauncherSignal>
) {
    val onboardingCompletedFlow: Flow<Boolean> = remember(preferencesRepository) {
        preferencesRepository.settings.map { settings: Settings -> settings.onboardingCompleted }
    }
    val isOnboardingCompleted: Boolean? by onboardingCompletedFlow.collectAsState(initial = null)
    LaunchedEffect(navController, launcherSignals) {
        launcherSignals.collect { signal: LauncherSignal ->
            val currentRoute: String? = latestBackStackEntry.value?.destination?.route
            if (
                shouldKeepOnboarding(
                    currentRoute = currentRoute,
                    onboardingRoute = onboardingRoute,
                    isOnboardingCompleted = isOnboardingCompleted
                )
            ) {
                return@collect
            }
            val wasShowingHome: Boolean = currentRoute == homeRoute
            if (signal !is LauncherSignal.TypedCharacter) {
                showDestination(navController = navController, route = homeRoute)
            }
            val signalForLauncher: LauncherSignal = if (signal is LauncherSignal.Home) {
                signal.narrowSearchToStartDestination(isStartDestination = wasShowingHome)
            } else {
                signal
            }
            LauncherSignalRequest(
                savedStateHandle = navController.getBackStackEntry(homeRoute).savedStateHandle
            ).request(signal = signalForLauncher)
        }
    }
}
