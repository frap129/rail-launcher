package feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import core.ui.model.data.Destination
import org.koin.androidx.compose.koinViewModel

val settingsDestination = Destination(
    route = "settings",
    content = { navController, _ ->
        SettingsScreen(navController)
    }
)

@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = koinViewModel()) {
    // TODO: Actual settings screen
}
