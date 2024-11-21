package core.ui

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import core.ui.model.data.Destination
import core.ui.theme.AppTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

/*
 * This is the root composable for this application. It creates a NavHost
 * within an AppFrame, allowing any screen or flow that is in the
 * `destinations` list to be accessed.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalComposeUiApi::class, KoinExperimentalAPI::class)
@Composable
fun App(destinations: List<Destination>) {
    val navController = rememberNavController()

    AppTheme {
        KoinAndroidContext {
            NavHost(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                navController = navController,
                startDestination = destinations.first().route
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
